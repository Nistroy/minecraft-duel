#!/usr/bin/env python3
"""Convertit un schéma Litematica (.litematic) en structure vanilla (.nbt) lisible par /duel admin arene.

Usage : litematic_to_structure.py <entrée.litematic> <sortie.nbt>
La sortie va dans <monde>/generated/duel/structures/<nom>.nbt (nom = champ "structure" de config/duel.json).
Une seule région prise en charge ; blocs d'air omis (l'arène est posée dans le vide) ; entités ignorées.
"""
import gzip
import struct
import sys

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


class Tag:
    """Valeur NBT typée : listes = (type des éléments, [Tag]), compounds = {nom: Tag}."""

    __slots__ = ("type", "value")

    def __init__(self, type_, value):
        self.type = type_
        self.value = value

    def __eq__(self, other):
        return isinstance(other, Tag) and self.type == other.type and self.value == other.value

    def __repr__(self):
        return f"Tag({self.type}, {self.value!r})"


_SCALARS = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}


def _read_payload(f, type_):
    if type_ in _SCALARS:
        fmt = _SCALARS[type_]
        return struct.unpack(fmt, f.read(struct.calcsize(fmt)))[0]
    if type_ == BYTE_ARRAY:
        (n,) = struct.unpack(">i", f.read(4))
        return f.read(n)
    if type_ == STRING:
        (n,) = struct.unpack(">H", f.read(2))
        return f.read(n).decode("utf-8")
    if type_ == LIST:
        elem = f.read(1)[0]
        (n,) = struct.unpack(">i", f.read(4))
        return (elem, [Tag(elem, _read_payload(f, elem)) for _ in range(n)])
    if type_ == COMPOUND:
        result = {}
        while True:
            t = f.read(1)[0]
            if t == END:
                return result
            (n,) = struct.unpack(">H", f.read(2))
            name = f.read(n).decode("utf-8")
            result[name] = Tag(t, _read_payload(f, t))
    if type_ in (INT_ARRAY, LONG_ARRAY):
        (n,) = struct.unpack(">i", f.read(4))
        fmt = ">%d%s" % (n, "i" if type_ == INT_ARRAY else "q")
        return list(struct.unpack(fmt, f.read(struct.calcsize(fmt))))
    raise ValueError(f"type NBT inconnu : {type_}")


def read_root(f):
    type_ = f.read(1)[0]
    (n,) = struct.unpack(">H", f.read(2))
    f.read(n)
    return Tag(type_, _read_payload(f, type_))


def _write_payload(out, tag):
    t, v = tag.type, tag.value
    if t in _SCALARS:
        out.append(struct.pack(_SCALARS[t], v))
    elif t == BYTE_ARRAY:
        out.append(struct.pack(">i", len(v)) + bytes(v))
    elif t == STRING:
        data = v.encode("utf-8")
        out.append(struct.pack(">H", len(data)) + data)
    elif t == LIST:
        elem, items = v
        out.append(bytes([elem]) + struct.pack(">i", len(items)))
        for item in items:
            _write_payload(out, item)
    elif t == COMPOUND:
        for name, child in v.items():
            data = name.encode("utf-8")
            out.append(bytes([child.type]) + struct.pack(">H", len(data)) + data)
            _write_payload(out, child)
        out.append(bytes([END]))
    elif t in (INT_ARRAY, LONG_ARRAY):
        out.append(struct.pack(">i%d%s" % (len(v), "i" if t == INT_ARRAY else "q"), len(v), *v))
    else:
        raise ValueError(f"type NBT inconnu : {t}")


def write_root(tag):
    out = [bytes([COMPOUND]) + struct.pack(">H", 0)]
    _write_payload(out, tag)
    return b"".join(out)


def unpack_states(longs, bits, count):
    """Indices de palette Litematica : `bits` bits par bloc, serrés, à cheval sur deux longs si besoin."""
    mask = (1 << bits) - 1
    unsigned = [v & 0xFFFFFFFFFFFFFFFF for v in longs]
    result = []
    for i in range(count):
        start = i * bits
        index, offset = start >> 6, start & 63
        value = unsigned[index] >> offset
        if offset + bits > 64:
            value |= unsigned[index + 1] << (64 - offset)
        result.append(value & mask)
    return result


def _int(v):
    return Tag(INT, v)


def _int_list(*values):
    return Tag(LIST, (INT, [_int(v) for v in values]))


def convert(litematic):
    regions = litematic.value["Regions"].value
    if len(regions) != 1:
        raise SystemExit(f"une seule région attendue, {len(regions)} trouvées")
    region = next(iter(regions.values())).value
    size = region["Size"].value
    sx, sy, sz = (abs(size[k].value) for k in ("x", "y", "z"))
    palette = region["BlockStatePalette"].value[1]
    bits = max(2, (len(palette) - 1).bit_length())
    states = unpack_states(region["BlockStates"].value, bits, sx * sy * sz)

    block_entities = {}
    for te in region["TileEntities"].value[1]:
        data = dict(te.value)
        pos = tuple(data.pop(k).value for k in ("x", "y", "z"))
        block_entities[pos] = Tag(COMPOUND, data)

    names = [entry.value["Name"].value for entry in palette]
    blocks = []
    for i, state in enumerate(states):
        if names[state] in AIR:
            continue
        x, rest = i % sx, i // sx
        z, y = rest % sz, rest // sz
        block = {"pos": _int_list(x, y, z), "state": _int(state)}
        if (x, y, z) in block_entities:
            block["nbt"] = block_entities[(x, y, z)]
        blocks.append(Tag(COMPOUND, block))

    return Tag(COMPOUND, {
        "DataVersion": _int(litematic.value["MinecraftDataVersion"].value),
        "size": _int_list(sx, sy, sz),
        "palette": Tag(LIST, (COMPOUND, palette)),
        "blocks": Tag(LIST, (COMPOUND, blocks)),
        "entities": Tag(LIST, (COMPOUND, [])),
    })


def main(argv):
    if len(argv) != 3:
        raise SystemExit(__doc__)
    with gzip.open(argv[1], "rb") as f:
        structure = convert(read_root(f))
    with gzip.open(argv[2], "wb") as f:
        f.write(write_root(structure))
    size = [t.value for t in structure.value["size"].value[1]]
    print(f"{argv[2]} : taille {size}, {len(structure.value['blocks'].value[1])} blocs")


if __name__ == "__main__":
    main(sys.argv)
