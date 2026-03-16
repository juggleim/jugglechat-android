package com.juggle.im.android;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * 基础模块边界冒烟测试。
 * 在 E00-T001 阶段用于约束模块骨架必须具备可加载的公共契约类型。
 */
public class ModuleBoundarySmokeTest {

    @Test
    public void requiredModuleContractsShouldBeLoadable() {
        List<String> requiredTypes = Arrays.asList(
                "com.juggle.im.core.domain.message.MessageTypeSpec",
                "com.juggle.im.core.domain.message.MessagePayload",
                "com.juggle.im.core.ui.message.MessageRenderer",
                "com.juggle.im.feature.chat.message.DefaultMessageRegistry"
        );

        for (String type : requiredTypes) {
            assertTrue("缺少模块契约类型: " + type, classExists(type));
        }
    }

    private boolean classExists(String typeName) {
        try {
            Class.forName(typeName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
