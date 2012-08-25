LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4

LOCAL_SRC_FILES := src/com/android/deskclock/ITimerClockService.aidl
LOCAL_SRC_FILES += $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := DeskClock

LOCAL_OVERRIDES_PACKAGES := AlarmClock

LOCAL_PROGUARD_FLAG_FILES := proguard.cfg

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
