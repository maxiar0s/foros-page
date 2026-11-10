from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class OracleInitRoutingTests(unittest.TestCase):
    def test_compose_routes_only_runner_to_init_hook_and_keeps_sql_explicit(self):
        compose = (ROOT / "compose.yaml").read_text(encoding="utf-8")
        self.assertIn("./database/container-init:/container-entrypoint-initdb.d:ro", compose)
        self.assertIn("./database/init/01-forum-schema-and-seed.sql:/opt/forum/01-forum-schema-and-seed.sql:ro", compose)
        self.assertNotIn("./database/init:/container-entrypoint-initdb.d", compose)

    def test_runner_connects_as_configured_app_user_to_pdb_without_tracing_secrets(self):
        runner = (ROOT / "database/container-init/00-forum-init.sh").read_text(encoding="utf-8")
        self.assertIn('CONNECT "${APP_USER}"/"${APP_USER_PASSWORD}"@//localhost:1521/FREEPDB1', runner)
        self.assertIn("set -eu", runner)
        self.assertNotIn("set -x", runner)
        self.assertIn("/opt/forum/01-forum-schema-and-seed.sql", runner)

    def test_deliverable_sql_exits_on_sql_and_os_errors(self):
        sql = (ROOT / "database/init/01-forum-schema-and-seed.sql").read_text(encoding="utf-8")
        self.assertIn("WHENEVER SQLERROR EXIT SQL.SQLCODE", sql.upper())
        self.assertIn("WHENEVER OSERROR EXIT FAILURE", sql.upper())


if __name__ == "__main__":
    unittest.main()
