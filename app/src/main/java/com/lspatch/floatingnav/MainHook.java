package com.lspatch.floatingnav;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * FloatingNavBar - LSPatch 通用模块
 * 把 Material 组件库的底部导航栏改为悬浮胶囊样式。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "FloatingNavBar";

    /** 已知的 Material 底部导航类名（含 support 旧包名） */
    private static final List<String> NAV_CLASSES = Arrays.asList(
            "com.google.android.material.bottomnavigation.BottomNavigationView",
            "com.google.android.material.navigation.NavigationBarView",
            "com.google.android.material.bottomappbar.BottomAppBar",
            "android.support.design.widget.BottomNavigationView",
            "android.support.design.bottomnavigation.BottomNavigationView");

    /** 防止重复处理的标记 */
    private static final int MARK_TAG = 0x7f0f0001;

    private static final int DP_MARGIN = 14;   // 悬浮边距
    private static final int DP_CORNER = 28;   // 圆角
    private static final int DP_ELEV = 10;     // 阴影

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam.packageName == null) return;
        // 不作用于系统框架和自身
        if (lpparam.packageName.equals("android")
                || lpparam.packageName.equals("com.lspatch.floatingnav")) return;

        // 1. Activity 每次 onPostResume 后扫描一次（覆盖页面切换）
        XposedHelpers.findAndHookMethod(Activity.class, "onPostResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity activity = (Activity) param.thisObject;
                activity.getWindow().getDecorView().post(new Runnable() {
                    @Override public void run() { scan(activity); }
                });
            }
        });

        // 2. 拦截 addView，处理延迟动态添加的导航栏
        XposedBridge.hookAllMethods(ViewGroup.class, "addView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object arg = param.args[0];
                if (arg instanceof View) {
                    View v = (View) arg;
                    if (isNavView(v)) makeFloating(v);
                }
            }
        });
    }

    /** 从 decorView 递归找出所有导航栏 */
    private static void scan(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        for (String clsName : NAV_CLASSES) {
            try {
                Class<?> cls = Class.forName(clsName, false,
                        activity.getClass().getClassLoader());
                for (View v : findAll(content, cls)) {
                    makeFloating(v);
                }
            } catch (Throwable ignored) { /* 该 App 未使用此控件 */ }
        }
    }

    private static List<View> findAll(ViewGroup root, Class<?> cls) {
        List<View> out = new ArrayList<>();
        int count = root.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = root.getChildAt(i);
            if (cls.isInstance(child)) out.add(child);
            if (child instanceof ViewGroup) out.addAll(findAll((ViewGroup) child, cls));
        }
        return out;
    }

    private static boolean isNavView(View v) {
        for (String clsName : NAV_CLASSES) {
            try {
                Class<?> cls = Class.forName(clsName, false, v.getClass().getClassLoader());
                if (cls.isInstance(v)) return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    /** 把导航栏改成悬浮胶囊 */
    private static void makeFloating(View nav) {
        if (nav.getTag(MARK_TAG) != null) return;   // 已处理
        nav.setTag(MARK_TAG, Boolean.TRUE);
        try {
            ViewGroup parent = (ViewGroup) nav.getParent();
            if (parent == null) return;

            Context ctx = nav.getContext();
            int margin = dp(ctx, DP_MARGIN);

            // 宽度留边距，高度自适应，底部居中悬浮
            ViewGroup.LayoutParams lp = nav.getLayoutParams();
            ViewGroup.MarginLayoutParams mlp;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                mlp = (ViewGroup.MarginLayoutParams) lp;
            } else {
                mlp = new ViewGroup.MarginLayoutParams(lp);
            }
            mlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            mlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mlp.setMargins(margin, 0, margin, margin);

            // 底部居中：按父容器类型设置 Gravity
            if (parent instanceof FrameLayout) {
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(mlp);
                flp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
                nav.setLayoutParams(flp);
            } else {
                try {
                    // CoordinatorLayout / 其他 support 容器：反射设置 bottom|center
                    Class<?> gCls = Class.forName(
                            "android.view.Gravity", false, nav.getClass().getClassLoader());
                    int gravity = android.view.Gravity.BOTTOM
                            | android.view.Gravity.CENTER_HORIZONTAL;
                    Object layoutParams = nav.getLayoutParams();
                    XposedHelpers.setIntField(layoutParams, "gravity", gravity);
                    nav.setLayoutParams((ViewGroup.LayoutParams) layoutParams);
                } catch (Throwable t) {
                    nav.setLayoutParams(mlp);
                }
            }

            // 圆角半透明背景
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(ctx, DP_CORNER));
            bg.setColor(0xE6242830);            // 深灰半透明，可按需改
            nav.setBackground(bg);
            nav.setClipToOutline(true);
            nav.setElevation(dp(ctx, DP_ELEV));

            // 父容器允许子 View 画出边界（阴影可见）
            parent.setClipToPadding(false);
            parent.setClipChildren(false);

            XposedBridge.log(TAG + ": floating ✓ " + nav.getContext().getPackageName());
        } catch (Throwable t) {
            XposedBridge.log(TAG + " error: " + t);
        }
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
