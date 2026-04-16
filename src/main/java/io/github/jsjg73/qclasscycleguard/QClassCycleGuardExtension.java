package io.github.jsjg73.qclasscycleguard;

import org.gradle.api.provider.Property;

/**
 * qclass-cycle-guard 플러그인의 설정 확장.
 *
 * <pre>
 * qclassCycleGuard {
 *     configPackage = 'com.example.config'
 * }
 * </pre>
 */
public abstract class QClassCycleGuardExtension {

    /** 생성될 QClassInitializer의 패키지. Spring component scan 범위 내여야 한다. */
    public abstract Property<String> getConfigPackage();
}
