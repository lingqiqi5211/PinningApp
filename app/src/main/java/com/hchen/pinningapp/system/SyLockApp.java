/*
 * This file is part of PinningApp.

 * PinningApp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2024 PinningApp Contributions
 */
package com.hchen.pinningapp.system;

import static com.hchen.pinningapp.utils.Device.isPad;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.hchen.pinningapp.hook.Hook;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class SyLockApp extends Hook {
    private int taskId;
    private boolean isObserver = false;
    boolean isLock = false;
    boolean needLockScreen = false;

    @Override
    public void init() {
        findAndHookMethod("com.android.server.wm.ActivityTaskManagerService",
                "onSystemReady",
                new HookAction() {
                    @Override
                    protected void after(XC_MethodHook.MethodHookParam param) {
                        try {
                            Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                            if (context == null) return;
                            if (!isObserver) {
                                ContentObserver contentObserver = new ContentObserver(new Handler(context.getMainLooper())) {
                                    @Override
                                    public void onChange(boolean selfChange, @Nullable Uri uri, int flags) {
                                        isLock = getLockApp(context) != -1;
                                        if (isLock) {
                                            taskId = getLockApp(context);
                                            XposedHelpers.callMethod(param.thisObject, "startSystemLockTaskMode", taskId);
                                            needLockScreen = getMyLockScreen(context) == 1;
                                        } else {
                                            new Handler(context.getMainLooper()).postDelayed(() -> XposedHelpers.callMethod(param.thisObject, "stopSystemLockTaskMode"),300);
                                        }
                                    }
                                };
                                context.getContentResolver().registerContentObserver(
                                        Settings.Global.getUriFor("key_lock_app"),
                                        false, contentObserver);
                                isObserver = true;
                            }
                        } catch (Throwable e) {
                            logE(tag, "E: " + e);
                        }
                    }
                }
        );

        findAndHookMethod("com.miui.server.input.util.ShortCutActionsUtils",
                "triggerHapticFeedback", boolean.class, String.class,
                String.class, boolean.class, String.class,
                new HookAction() {
                    @Override
                    protected void after(XC_MethodHook.MethodHookParam param) {
                        /*if (!mPrefsMap.getBoolean("system_framework_guided_access_status"))
                            return; // 不知道为什么还是需要重启才生效*/
                        String shortcut = (String) param.args[1];
                        if ("imperceptible_press_power_key".equals(shortcut) || "long_press_power_key".equals(shortcut)) {
                            Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                            isLock = getLockApp(context) != -1;
                            if (isLock) {
                                setLockApp(context, -1);
                            }
                        }
                    }
                }
        );

        findAndHookMethod("com.android.server.wm.LockTaskController",
                "shouldLockKeyguard", int.class,
                new HookAction() {
                    @Override
                    protected void before(XC_MethodHook.MethodHookParam param) {
                        if (needLockScreen) {
                            param.setResult(true);
                        } else {
                            param.setResult(false);
                        }
                    }
                }
        );

        if (isPad()) {
            findAndHookMethod("com.android.server.wm.MiuiCvwGestureController$GesturePointerEventListener",
                    "onPointerEvent", MotionEvent.class,
                    new HookAction() {
                        @Override
                        protected void before(XC_MethodHook.MethodHookParam param) {
                            if (isLock) {
                                param.setResult(null);
                            }
                        }
                    }
            );
        }
    }

    public static void setLockApp(Context context, int id) {
        Settings.Global.putInt(context.getContentResolver(), "key_lock_app", id);
    }

    public int getLockApp(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), "key_lock_app");
        } catch (Settings.SettingNotFoundException e) {
            logE(tag, "getLockApp E: " + e);
        }
        return -1;
    }

    public int getMyLockScreen(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), "exit_lock_app_screen");
        } catch (Settings.SettingNotFoundException e) {
            logE(tag, "getMyLockScreen E will set " + e);
        }
        return 0;
    }

}
