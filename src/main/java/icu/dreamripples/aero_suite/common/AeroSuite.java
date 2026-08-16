package icu.dreamripples.aero_suite.common;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Aeronautics Suite 公共常量/工具。一个 jar 三个 mod(见 {@link AeroSuiteIds}),
 * 各自入口类在 gravity / simplification / starlight 包,共享本类的 LOGGER。
 */
public final class AeroSuite {
    public static final Logger LOGGER = LogUtils.getLogger();

    private AeroSuite() {}
}
