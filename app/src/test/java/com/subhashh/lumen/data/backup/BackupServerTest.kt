package com.subhashh.lumen.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Backup servers (rclone). Every call names its server as an rclone connection string
 * (`:kind,key='value',…:path`), built from what the person typed: a quote, comma or colon in a
 * password or address must stay inside its value, never add or change an option. Passwords that
 * rclone needs "obscured" go through the native library, so these cases use S3 (its secret is
 * passed as is) or no password.
 */
@RunWith(RobolectricTestRunner::class)
class BackupServerTest {

    /** rclone's connection-string syntax, read back: the backend, its options and the path. */
    private data class Parsed(val kind: String, val options: Map<String, String>, val path: String)

    private fun parse(remote: String): Parsed {
        require(remote.startsWith(":"))
        var i = 1
        val kind = StringBuilder()
        while (remote[i] != ',' && remote[i] != ':') kind.append(remote[i++])
        val options = LinkedHashMap<String, String>()
        while (remote[i] == ',') {
            i++
            val key = StringBuilder()
            while (remote[i] != '=') key.append(remote[i++])
            i++ // =
            require(remote[i] == '\'') { "unquoted value for $key" }
            i++
            val value = StringBuilder()
            while (true) {
                if (remote[i] == '\'') {
                    if (i + 1 < remote.length && remote[i + 1] == '\'') { value.append('\''); i += 2; continue }
                    i++
                    break
                }
                value.append(remote[i++])
            }
            require(options.put(key.toString(), value.toString()) == null) { "option $key twice" }
        }
        require(remote[i] == ':')
        return Parsed(kind.toString(), options, remote.substring(i + 1))
    }

    private fun s3(address: String = "https://s3.example.com", user: String = "AKIA", password: String = "secret", path: String = "bucket/lumen", region: String = "") =
        BackupServer(BackupServer.Kind.S3, address, user, password, path, region = region)

    @Test
    fun awkwardCharactersStayInsideTheirValues() {
        val nasty = listOf("it's", "a,b", "c:d", "''", "x',endpoint='https://evil.example", "ü ñ 漢", "trailing'")
        for (text in nasty) {
            val parsed = parse(s3(user = text, password = "p$text", region = "r$text").remote())
            assertEquals(text, parsed.options["access_key_id"])
            assertEquals("p$text", parsed.options["secret_access_key"])
            assertEquals("r$text", parsed.options["region"])
            assertEquals("https://s3.example.com", parsed.options["endpoint"])
        }
    }

    @Test
    fun aPasswordCantAddOrChangeAnOption() {
        val parsed = parse(s3(password = "x',endpoint='https://evil.example',provider='AWS").remote())
        assertEquals(setOf("provider", "endpoint", "access_key_id", "secret_access_key", "no_check_bucket"), parsed.options.keys)
        assertEquals("Other", parsed.options["provider"])
        assertEquals("https://s3.example.com", parsed.options["endpoint"])
    }

    @Test
    fun amazonNeedsNoEndpointAndBucketsArentCreated() {
        val parsed = parse(s3(address = "  ").remote())
        assertEquals("AWS", parsed.options["provider"])
        assertFalse("endpoint" in parsed.options)
        assertEquals("true", parsed.options["no_check_bucket"])
        assertEquals("s3", parsed.kind)
    }

    @Test
    fun thePathLosesOnlyItsOuterSlashes() {
        assertEquals("bucket/lumen", parse(s3(path = "/bucket/lumen/").remote()).path)
        assertEquals("", parse(s3(path = "/").remote()).path)
        assertEquals("a/b:c", parse(s3(path = "a/b:c").remote()).path)
    }

    @Test
    fun serversWithoutAPasswordNeedNoObscuring() {
        val smb = parse(BackupServer(BackupServer.Kind.Smb, "nas.local", "", "", "share/backups", port = 445).remote())
        assertEquals(mapOf("host" to "nas.local", "port" to "445", "user" to "guest"), smb.options)
        assertEquals("share/backups", smb.path)
        val sftp = parse(BackupServer(BackupServer.Kind.Sftp, "pi.lan", "me", "", "/home/me/lumen").remote())
        assertEquals(mapOf("host" to "pi.lan", "user" to "me"), sftp.options)
    }

    @Test
    fun nextcloudIsRecognisedFromItsAddress() {
        fun vendor(url: String) = parse(BackupServer(BackupServer.Kind.WebDav, url, "", "", "Lumen").remote()).options["vendor"]
        assertEquals("nextcloud", vendor("https://cloud.example.com/remote.php/dav/files/me"))
        assertEquals("nextcloud", vendor("https://cloud.example.com/remote.php/webdav"))
        assertEquals("other", vendor("https://dav.example.com/backups"))
    }

    @Test
    fun aServerSurvivesBeingStored() {
        val servers = listOf(
            s3(user = "k'ey", password = "s,e:c'ret", region = "eu-central-003"),
            BackupServer(BackupServer.Kind.WebDav, "https://dav.example.com/x", "me", "pw", "Lumen"),
            BackupServer(BackupServer.Kind.Sftp, "pi.lan", "me", "pw", "/srv/lumen", port = 2222),
            BackupServer(BackupServer.Kind.Smb, "nas", "", "", "share"),
        )
        for (server in servers) assertEquals(server, BackupServer.parse(server.toJson()))
    }

    @Test
    fun theLabelNamesThePlaceButNeverTheLogin() {
        val server = BackupServer(BackupServer.Kind.WebDav, "https://me:pw@dav.example.com/remote.php/dav", "me", "hunter2", "/photos/lumen/")
        val label = server.label()
        assertEquals("dav.example.com › photos/lumen", label)
        assertFalse(label.contains("hunter2"))
        assertTrue(s3(address = "").label().startsWith("Amazon S3"))
        assertEquals("s3.example.com › bucket/lumen", s3().label())
    }
}
