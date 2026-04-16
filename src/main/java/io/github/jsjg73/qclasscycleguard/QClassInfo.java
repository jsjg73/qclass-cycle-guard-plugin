package io.github.jsjg73.qclasscycleguard;

/**
 * Q-class 하나의 정보를 담는 값 객체.
 *
 * <p>예: QCmsManager.java 파일의 경우
 * <ul>
 *   <li>name = "QCmsManager"</li>
 *   <li>pkg = "com.example.entity.cms"</li>
 *   <li>fqcn = "com.example.entity.cms.QCmsManager"</li>
 * </ul>
 */
class QClassInfo {

    final String name;
    final String pkg;

    QClassInfo(String name, String pkg) {
        this.name = name;
        this.pkg = pkg;
    }

    /** 패키지명 + 클래스명 (예: com.example.QFoo) */
    String fqcn() {
        return pkg + "." + name;
    }
}
