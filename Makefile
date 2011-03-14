VERSION = 2.1.2
PROJECT_NAME = BlueNMEA
JAVA_PACKAGE = name.kellermann.max.bluenmea
JNI_NAME = bluebridge

CLASS_NAME = $(JAVA_PACKAGE).Bridge
CLASS_SOURCE = $(subst .,/,$(CLASS_NAME)).java
CLASS_CLASS = $(patsubst %.java,%.class,$(CLASS_SOURCE))
CLASS_HEADER = $(subst .,_,$(CLASS_NAME)).h

JAVA_SOURCES = $(wildcard src/*.java)
JAVA_CLASSES = $(patsubst src/%.java,bin/classes/$(subst .,/,$(JAVA_PACKAGE))/%.class,$(JAVA_SOURCES))

SDK_ROOT = $(HOME)/opt/android-sdk-linux_x86
NDK_ROOT = $(HOME)/opt/android-ndk-1.5_r1

.PHONY: all clean realclean update install reinstall uninstall release

all: bin/$(PROJECT_NAME)-debug.apk

clean:
	rm -rf bin gen libs
	rm -f jni/$(CLASS_HEADER)

realclean: clean
	rm -f build.xml default.properties local.properties

build.xml: AndroidManifest.xml
	$(SDK_ROOT)/tools/android update project --path `pwd` --target 5

update:| realclean build.xml

bin/stamp-compile: $(JAVA_SOURCES) build.xml
	ant -quiet compile
	@touch $@

jni/$(CLASS_HEADER): bin/stamp-compile
	@rm -f $@
	javah -classpath bin/classes -d jni $(CLASS_NAME)

libs/armeabi/lib$(JNI_NAME).so: jni/$(CLASS_HEADER) $(wildcard jni/*.c)
	ndk-build

bin/$(PROJECT_NAME)-debug.apk: libs/armeabi/lib$(JNI_NAME).so build.xml $(JAVA_SOURCES)
	ant -quiet debug

install: bin/$(PROJECT_NAME)-debug.apk
	$(SDK_ROOT)/platform-tools/adb install $<

reinstall: bin/$(PROJECT_NAME)-debug.apk
	$(SDK_ROOT)/platform-tools/adb install -r $<

uninstall:
	$(SDK_ROOT)/platform-tools/adb uninstall $(JAVA_PACKAGE)

release: libs/armeabi/lib$(JNI_NAME).so build.xml
	ant -quiet release
	jarsigner -verbose -keystore ~/.android/mk.keystore -signedjar bin/BlueNMEA-$(VERSION).apk bin/BlueNMEA-unsigned.apk mk
