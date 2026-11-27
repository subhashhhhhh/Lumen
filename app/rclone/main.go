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
	"strconv"
	"unsafe"

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

func main() {}
