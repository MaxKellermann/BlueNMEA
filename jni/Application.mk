APP_PROJECT_PATH := $(call my-dir)/..
APP_MODULES := bluebridge
APP_CPPFLAGS := -I$(APP_PROJECT_PATH$)/gen/include
APP_CFLAGS := -std=gnu99 -Wall -Wextra
APP_CFLAGS += -Wmissing-prototypes -Wwrite-strings -Wcast-qual -Wpointer-arith -Wbad-function-cast -Wsign-compare -Waggregate-return -Wmissing-declarations -Wstrict-prototypes
APP_CFLAGS += -Wno-long-long -Wno-variadic-macros
APP_CFLAGS += -Werror
