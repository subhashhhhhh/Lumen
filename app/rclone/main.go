// Package main is rclone for Lumen Backup: only the backends Lumen offers, called over JNI.
//
// Kotlin side: com.subhashh.lumen.data.backup.Rclone (nativeInit, nativeRpc). Rename both
// together, or nativeRpc throws UnsatisfiedLinkError.
//
// Strings cross as UTF-8 byte arrays, never jstring: JNI's modified UTF-8 breaks on emoji.
package main

/*
#include <jni.h>
#include <stdlib.h>
#include <string.h>

static char* bytesOf(JNIEnv* env, jbyteArray a, jsize* len) {
	*len = (*env)->GetArrayLength(env, a);
	char* out = malloc(*len + 1);
	(*env)->GetByteArrayRegion(env, a, 0, *len, (jbyte*)out);
	out[*len] = 0;
	return out;
}

static jbyteArray newBytes(JNIEnv* env, const char* data, jsize len) {
	jbyteArray a = (*env)->NewByteArray(env, len);
	if (a != NULL) (*env)->SetByteArrayRegion(env, a, 0, len, (const jbyte*)data);
	return a;
}
*/
import "C"

import (
	"context"
	"errors"
	"net"
	"strconv"
	"time"
	"unsafe"

	"github.com/rclone/rclone/fs/rc"
	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/knownhosts"

	"github.com/rclone/rclone/librclone/librclone"

	_ "github.com/rclone/rclone/backend/local"
	_ "github.com/rclone/rclone/backend/s3"
	_ "github.com/rclone/rclone/backend/sftp"
	_ "github.com/rclone/rclone/backend/smb"
	_ "github.com/rclone/rclone/backend/webdav"
	_ "github.com/rclone/rclone/fs/operations" // operations/* rc commands
)

//export Java_com_subhashh_lumen_data_backup_Rclone_nativeInit
func Java_com_subhashh_lumen_data_backup_Rclone_nativeInit(env *C.JNIEnv, cls C.jclass) {
	librclone.Initialize()
}

// Java_com_subhashh_lumen_data_backup_Rclone_nativeRpc returns the HTTP-style status as three
// ASCII digits, then the JSON output.
//
//export Java_com_subhashh_lumen_data_backup_Rclone_nativeRpc
func Java_com_subhashh_lumen_data_backup_Rclone_nativeRpc(env *C.JNIEnv, cls C.jclass, method C.jbyteArray, input C.jbyteArray) C.jbyteArray {
	var ml, il C.jsize
	m := C.bytesOf(env, method, &ml)
	i := C.bytesOf(env, input, &il)
	output, status := librclone.RPC(C.GoStringN(m, C.int(ml)), C.GoStringN(i, C.int(il)))
	C.free(unsafe.Pointer(m))
	C.free(unsafe.Pointer(i))
	if status < 100 || status > 999 {
		status = 500
	}
	result := strconv.Itoa(status) + output
	cs := C.CString(result)
	defer C.free(unsafe.Pointer(cs))
	return C.newBytes(env, cs, C.jsize(len(result)))
}

// lumen/hostkey {"host": "nas.local", "port": 22} → {"line": "<known_hosts line>", "fingerprint": "SHA256:…"}
//
// The SFTP server's key, read during the handshake (no login), so Lumen can keep it on first
// connect and rclone (known_hosts_file) refuses a server whose key later changes.
func init() {
	rc.Add(rc.Call{
		Path:  "lumen/hostkey",
		Title: "Read an SSH server's host key",
		Fn: func(ctx context.Context, in rc.Params) (rc.Params, error) {
			host, err := in.GetString("host")
			if err != nil {
				return nil, err
			}
			port, err := in.GetInt64("port")
			if err != nil || port <= 0 {
				port = 22
			}
			addr := net.JoinHostPort(host, strconv.FormatInt(port, 10))
			var key ssh.PublicKey
			errGot := errors.New("got the key")
			config := &ssh.ClientConfig{
				User:    "lumen",
				Timeout: 15 * time.Second,
				HostKeyCallback: func(_ string, _ net.Addr, k ssh.PublicKey) error {
					key = k
					return errGot
				},
			}
			client, err := ssh.Dial("tcp", addr, config)
			if client != nil {
				_ = client.Close()
			}
			if key == nil {
				return nil, err
			}
			return rc.Params{
				"line":        knownhosts.Line([]string{knownhosts.Normalize(addr)}, key),
				"fingerprint": ssh.FingerprintSHA256(key),
			}, nil
		},
	})
}

func main() {}
