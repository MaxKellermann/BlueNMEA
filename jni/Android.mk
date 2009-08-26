LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -lbluetooth
LOCAL_MODULE := bluebridge
LOCAL_SRC_FILES := bluebridge.c

include $(BUILD_SHARED_LIBRARY)
