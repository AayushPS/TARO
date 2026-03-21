import inspect
import unittest
from pathlib import Path

import src.main.python.Utils as Utils
from src.main.python.Utils import IDMapper


class TestPythonPackageLayout(unittest.TestCase):

    def test_utils_package_resolves_from_src_main_python(self):
        repo_root = Path(__file__).resolve().parents[4]
        expected_root = repo_root / "src" / "main" / "python"
        utils_init = Path(inspect.getfile(Utils)).resolve()

        self.assertTrue(
            utils_init.is_relative_to(expected_root),
            f"Utils package should resolve from {expected_root}, got {utils_init}",
        )

    def test_idmapper_import_contract(self):
        mapper = IDMapper()
        self.assertEqual("src.main.python.Utils.IDMapper", IDMapper.__module__)
        self.assertEqual(0, mapper.get_or_create("package-layout-check"))
