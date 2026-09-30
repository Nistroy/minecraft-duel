import gzip
import io
import unittest

import litematic_to_structure as lts
from litematic_to_structure import COMPOUND, INT, LIST, LONG_ARRAY, STRING, Tag


def pack_states(values, bits):
    """Inverse de unpack_states, pour fabriquer des schémas de test."""
    total = 0
    for i, v in enumerate(values):
        total |= v << (i * bits)
    count = (len(values) * bits + 63) // 64
    longs = []
    for i in range(count):
        word = (total >> (64 * i)) & 0xFFFFFFFFFFFFFFFF
        longs.append(word - (1 << 64) if word >= 1 << 63 else word)
    return longs


def block(name, **props):
    entry = {"Name": Tag(STRING, name)}
    if props:
        entry["Properties"] = Tag(COMPOUND, {k: Tag(STRING, v) for k, v in props.items()})
    return Tag(COMPOUND, entry)


def litematic(size, palette, states, tile_entities=()):
    bits = max(2, (len(palette) - 1).bit_length())
    sx, sy, sz = size
    region = {
        "Position": Tag(COMPOUND, {k: Tag(INT, 0) for k in "xyz"}),
        # Litematica accepte des tailles négatives : seul le module compte.
        "Size": Tag(COMPOUND, {"x": Tag(INT, -sx), "y": Tag(INT, sy), "z": Tag(INT, sz)}),
        "BlockStatePalette": Tag(LIST, (COMPOUND, palette)),
        "BlockStates": Tag(LONG_ARRAY, pack_states(states, bits)),
        "TileEntities": Tag(LIST, (COMPOUND, list(tile_entities))),
        "Entities": Tag(LIST, (COMPOUND, [])),
    }
    return Tag(COMPOUND, {
        "MinecraftDataVersion": Tag(INT, 3955),
        "Regions": Tag(COMPOUND, {"r": Tag(COMPOUND, region)}),
    })


class UnpackTest(unittest.TestCase):
    def test_values_spanning_two_longs(self):
        values = [i % 100 for i in range(200)]
        self.assertEqual(lts.unpack_states(pack_states(values, 7), 7, 200), values)


class NbtTest(unittest.TestCase):
    def test_round_trip(self):
        tag = Tag(COMPOUND, {
            "s": Tag(STRING, "é"),
            "l": Tag(LIST, (INT, [Tag(INT, 1), Tag(INT, -2)])),
            "e": Tag(LIST, (COMPOUND, [])),
            "a": Tag(LONG_ARRAY, [1, -1]),
        })
        self.assertEqual(lts.read_root(io.BytesIO(lts.write_root(tag))), tag)


class ConvertTest(unittest.TestCase):
    def setUp(self):
        palette = [block("minecraft:air"), block("minecraft:stone"), block("minecraft:skeleton_skull", rotation="4")]
        # 2×2×2, index = (y*sz + z)*sx + x ; pierre en (1,0,0), crâne en (0,1,1).
        states = [0, 1, 0, 0, 0, 0, 2, 0]
        skull = Tag(COMPOUND, {
            "id": Tag(STRING, "minecraft:skull"),
            "x": Tag(INT, 0), "y": Tag(INT, 1), "z": Tag(INT, 1),
            "custom_name": Tag(STRING, "os"),
        })
        self.structure = lts.convert(litematic((2, 2, 2), palette, states, [skull])).value

    def blocks(self):
        return {tuple(t.value for t in b.value["pos"].value[1]): b.value for b in self.structure["blocks"].value[1]}

    def test_size_is_absolute(self):
        self.assertEqual([t.value for t in self.structure["size"].value[1]], [2, 2, 2])

    def test_air_is_skipped_and_positions_follow_litematica_order(self):
        blocks = self.blocks()
        self.assertEqual(set(blocks), {(1, 0, 0), (0, 1, 1)})
        self.assertEqual(blocks[(1, 0, 0)]["state"].value, 1)

    def test_block_entity_kept_without_coordinates(self):
        nbt = self.blocks()[(0, 1, 1)]["nbt"].value
        self.assertEqual(set(nbt), {"id", "custom_name"})

    def test_output_is_readable_gzip_nbt(self):
        data = gzip.compress(lts.write_root(Tag(COMPOUND, self.structure)))
        root = lts.read_root(gzip.GzipFile(fileobj=io.BytesIO(data)))
        self.assertEqual(root.value["DataVersion"].value, 3955)


if __name__ == "__main__":
    unittest.main()
