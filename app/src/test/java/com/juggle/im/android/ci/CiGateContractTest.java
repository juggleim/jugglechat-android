package com.juggle.im.android.ci;

import org.junit.Test;

import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * CI 门禁契约测试：确保流水线与质量检查脚本已落地。
 */
public class CiGateContractTest {

    @Test
    public void workflowAndQualityScriptShouldExist() {
        assertTrue(Files.exists(resolveProjectPath(".github/workflows/android-ci.yml")));
        assertTrue(Files.exists(resolveProjectPath("scripts/ci/check-quality.sh")));
    }

    @Test
    public void qualityScriptShouldContainLintUnitAssembleGate() throws IOException {
        String script = new String(Files.readAllBytes(resolveProjectPath("scripts/ci/check-quality.sh")));
        assertTrue(script.contains(":app:lintDebug"));
        assertTrue(script.contains(":app:testDebugUnitTest"));
        assertTrue(script.contains(":app:assembleDebug"));
    }

    private Path resolveProjectPath(String relativePath) {
        Path direct = Paths.get(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = Paths.get("..", relativePath);
        if (Files.exists(parent)) {
            return parent.normalize();
        }
        return direct;
    }
}
