VERSION = 1.1
PROJECT_NAME = BlueNMEA
JAVA_PACKAGE = name.kellermann.max.bluenmea
JNI_NAME = bluebridge

CLASS_NAME = $(JAVA_PACKAGE).Bridge
CLASS_SOURCE = $(subst .,/,$(CLASS_NAME)).java
CLASS_CLASS = $(patsubst %.java,%.class,$(CLASS_SOURCE))
CLASS_HEADER = $(subst .,_,$(CLASS_NAME)).h

JAVA_SOURCES = $(wildcard src/$(subst .,/,$(JAVA_PACKAGE))/*.java)
JAVA_CLASSES = $(patsubst src/%.java,bin/classes/%.class,$(JAVA_SOURCES))

SDK_ROOT = $(HOME)/opt/android-sdk-linux_x86-1.5_r3
NDK_ROOT = $(HOME)/opt/android-ndk-1.5_r1

.PHONY: all clean realclean update install reinstall uninstall release

all: bin/.$(PROJECT_NAME)-debug.apk

clean:
	rm -rf bin gen libs

realclean: clean
	rm -f build.xml default.properties local.properties

build.xml:
	$(SDK_ROOT)/tools/android update project --path `pwd` --target 2

update:| realclean build.xml

bin/classes/$(CLASS_CLASS): $(JAVA_SOURCES) build.xml
	ant -quiet compile

compile: bin/classes/$(CLASS_CLASS)

gen/include/$(CLASS_HEADER): bin/classes/$(CLASS_CLASS)
	@mkdir -p gen/include
	javah -classpath bin/classes -d gen/include $(CLASS_NAME)

libs/armeabi/lib$(JNI_NAME).so: gen/include/$(CLASS_HEADER) $(wildcard jni/*.c)
	rm -f $(NDK_ROOT)/apps/$(JNI_NAME) $(NDK_ROOT)/sources/$(JNI_NAME)
	ln -s `pwd`/jni $(NDK_ROOT)/apps/$(JNI_NAME)
	ln -s `pwd`/jni $(NDK_ROOT)/sources/$(JNI_NAME)
	make -C $(NDK_ROOT) APP=$(JNI_NAME)

bin/.$(PROJECT_NAME)-debug.apk: libs/armeabi/lib$(JNI_NAME).so build.xml
	ant -quiet debug

install: bin/.$(PROJECT_NAME)-debug.apk
	$(SDK_ROOT)/tools/adb install $<

reinstall: bin/.$(PROJECT_NAME)-debug.apk
	$(SDK_ROOT)/tools/adb install -r $<

uninstall:
	$(SDK_ROOT)/tools/adb uninstall $(JAVA_PACKAGE)

release: libs/armeabi/lib$(JNI_NAME).so build.xml
	ant -quiet release
	jarsigner -verbose -keystore ~/.android/mk.keystore -signedjar bin/BlueNMEA-$(VERSION).apk bin/.BlueNMEA-unsigned.apk mk
