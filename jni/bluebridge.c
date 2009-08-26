/*
 * Copyright (C) 2003-2009 Max Kellermann <max@duempel.org>
 * http://max.kellermann.name/projects/blue-nmea/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

/*
 * This is glue code between JNI and libbluetooth.  Since Android 1.5
 * has no Java API for Bluetooth, we roll our own.
 *
 */

#include "name_kellermann_max_bluenmea_BlueNMEA.h"

#include <stdbool.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/rfcomm.h>
#include <bluetooth/hci.h>
#include <bluetooth/hci_lib.h>

/**
 * Throws an IOException with the specified message.
 */
static void
throw(JNIEnv *env, const char *class_name, const char *msg)
{
	jclass cls;

	cls = (*env)->FindClass(env, class_name);
	if (cls != NULL)
		(*env)->ThrowNew(env, cls, msg);

	(*env)->DeleteLocalRef(env, cls);
}

/**
 * Throws an IOException with the specified message.
 */
static void
throw_ioexception(JNIEnv *env, const char *msg)
{
	throw(env, "java/io/IOException", msg);
}

/**
 * Throws strerror(errno) in an IOException.
 */
static void
throw_errno(JNIEnv *env)
{
	throw_ioexception(env, strerror(errno));
}

/**
 * Converts a #bdaddr_t into a Java String.
 */
static jstring
bdaddr_t_to_jstring(JNIEnv *env, const bdaddr_t *src)
{
	char buffer[19];
	ba2str(src, buffer);
	return (*env)->NewStringUTF(env, buffer);
}

/**
 * Converts a #sockaddr_rc struct into a Java String.
 */
static jstring
sockaddr_rc_to_jstring(JNIEnv *env, const struct sockaddr_rc *src)
{
	return bdaddr_t_to_jstring(env, &src->rc_bdaddr);
}

/**
 * Parses a Java String into a #sockaddr_rc struct.
 *
 * @return true on success
 */
static bool
jstring_to_sockaddr_rc(struct sockaddr_rc *dest, JNIEnv *env, jstring src)
{
	const char *native = (*env)->GetStringUTFChars(env, src, 0);
	int ret;

	dest->rc_family = AF_BLUETOOTH;
	dest->rc_channel = (uint8_t)1;
	ret = str2ba(native, &dest->rc_bdaddr);

	(*env)->ReleaseStringUTFChars(env, src, native);

	return ret == 0;
}

JNIEXPORT jobjectArray JNICALL
Java_name_kellermann_max_bluenmea_BlueNMEA_scan(JNIEnv *env, jobject obj)
{
	inquiry_info *ii = NULL;
	int dev_id, num_rsp;
	jobjectArray devices;

	(void)obj;

	dev_id = hci_get_route(NULL);
	if (dev_id < 0) {
		throw_ioexception(env, "hci_get_route() has failed");
		return NULL;
	}

	/* wait for 3 seconds, hope this is long enough (Android
	   interrupts us for longer delays) */
	num_rsp = hci_inquiry(dev_id, 3, 256, NULL, &ii, IREQ_CACHE_FLUSH);
	if (num_rsp < 0) {
		throw_ioexception(env, "hci_inquiry() has failed");
		return NULL;
	}

	devices = (*env)->NewObjectArray(env, num_rsp,
					 (*env)->FindClass(env, "java/lang/String"),
					 (*env)->NewStringUTF(env, ""));
	for (int i = 0; i < num_rsp; i++) {
		jstring s = bdaddr_t_to_jstring(env, &ii[i].bdaddr);
		(*env)->SetObjectArrayElement(env, devices, i, s);

	}

	free(ii);

	return devices;
}

/* these socket descriptors should be moved to class properties */
static int listen_fd = -1, sockfd = -1;

JNIEXPORT void JNICALL
Java_name_kellermann_max_bluenmea_BlueNMEA_open(JNIEnv *env, jobject obj,
						jstring address_string)
{
	struct sockaddr_rc addr;

	(void)obj;

	if (!jstring_to_sockaddr_rc(&addr, env, address_string)) {
		throw(env, "java/lang/IllegalArgumentException",
		      "Invalud bluetooth address");
		return;
	}

	if (sockfd >= 0)
		close(sockfd);

	sockfd = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
	if (sockfd < 0) {
		throw_errno(env);
		return;
	}

	int ret = connect(sockfd, (struct sockaddr *)&addr, sizeof(addr));
	if (ret < 0) {
		throw_errno(env);

		close(sockfd);
		sockfd = -1;
		return;
	}
}

JNIEXPORT void JNICALL
Java_name_kellermann_max_bluenmea_BlueNMEA_listen(JNIEnv *env, jobject obj)
{
	(void)env;
	(void)obj;

	if (listen_fd >= 0)
		close(listen_fd);

	listen_fd = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);
	if (listen_fd < 0) {
		throw_errno(env);
		return;
	}

	struct sockaddr_rc loc_addr;
	loc_addr.rc_family = AF_BLUETOOTH;
	loc_addr.rc_bdaddr = *BDADDR_ANY;
	loc_addr.rc_channel = (uint8_t) 1;

	int ret = bind(listen_fd, (struct sockaddr *)&loc_addr,
		       sizeof(loc_addr));
	if (ret < 0) {
		throw_errno(env);

		close(listen_fd);
		listen_fd = -1;
		return;
	}

	ret = listen(listen_fd, 1);
	if (ret < 0) {
		throw_errno(env);
		return;
	}
}

JNIEXPORT jstring JNICALL
Java_name_kellermann_max_bluenmea_BlueNMEA_accept(JNIEnv *env, jobject obj)
{
	struct sockaddr_rc remote_address;
	socklen_t remote_address_length;

	(void)env;
	(void)obj;

	if (listen_fd < 0) {
		throw(env, "java/lang/IllegalStateException",
		      "No listener socket");
		return NULL;
	}

	if (sockfd >= 0)
		close(sockfd);

	remote_address_length = sizeof(remote_address);
	sockfd = accept(listen_fd, (struct sockaddr *)&remote_address,
			&remote_address_length);
	if (sockfd < 0) {
		throw_errno(env);
		return NULL;
	}

	return sockaddr_rc_to_jstring(env, &remote_address);
}

JNIEXPORT void JNICALL
Java_name_kellermann_max_bluenmea_BlueNMEA_close(JNIEnv *env, jobject obj)
{
	(void)env;
	(void)obj;

	if (sockfd >= 0) {
		close(sockfd);
		sockfd = -1;
	}
}

JNIEXPORT void JNICALL
Java_name_kellermann_max_bluenmea_BlueNMEA_send(JNIEnv *env, jobject obj, jstring line)
{
	ssize_t nbytes;

	(void)obj;

	if (sockfd < 0) {
		throw(env, "java/lang/IllegalStateException",
		      "Not connected");
		return;
	}

	const char *line_native = (*env)->GetStringUTFChars(env, line, 0);
	nbytes = send(sockfd, line_native, strlen(line_native), 0);
	(*env)->ReleaseStringUTFChars(env, line, line_native);

	if (nbytes < 0)
		throw_errno(env);
}
