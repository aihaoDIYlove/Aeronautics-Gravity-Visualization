package icu.dreamripples.aero_suite.common.client;

import icu.dreamripples.aero_suite.common.config.AeroSuiteConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 配置屏注册(client-only 落点)。
 *
 * <p>必须独立成类: {@code registerExtensionPoint} 的 lambda 实现方法签名含 vanilla
 * {@code Screen}(IConfigScreenFactory.createScreen 的第二参数), 该 lambda 若编译进三个
 * @Mod 公共入口类, 入口类自身在 DEDICATED_SERVER 上类校验时就要解析 Screen -> dist-clean
 * RuntimeException, 三 mod 全部构造失败(运行期 if(dist.isClient()) 守卫救不了 -- 类文件里
 * 的方法描述符在类加载时就会被校验)。
 *
 * <p>本类只在客户端被入口类的 dist 守卫调用(见各 @Mod 入口类 registerConfigScreen),
 * 专用服永不加载。改配置屏注册方式前重读本注释。
 */
public final class ConfigScreenRegistrar {
    private ConfigScreenRegistrar() {}

    public static void register(ModContainer container) {
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (c, last) -> new AeroSuiteConfigScreen(last));
    }
}
