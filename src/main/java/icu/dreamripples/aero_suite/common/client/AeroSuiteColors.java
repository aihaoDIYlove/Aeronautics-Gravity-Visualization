package icu.dreamripples.aero_suite.common.client;

/**
 * 客户端颜色工具。三处 visual/renderer 共用的 ARGB 线性插值
 * (红石配重/轻质配重灯带染色、稳定器质量/浮力档位染色)。
 */
public final class AeroSuiteColors {

    /** ARGB 线性插值(t=0 返回 a,t=1 返回 b,结果恒不透明)。 */
    public static int mixArgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private AeroSuiteColors() {}
}
