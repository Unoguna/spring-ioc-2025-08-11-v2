# Mini IoC Container — README

이 프로젝트는 `@Component` 스캔 + **생성자 주입** + **싱글톤**만 지원하는 **초간단 IoC 컨테이너**입니다.
아래 `ApplicationContext` 코드 기준으로 작동 방식을 설명합니다.

---

## 파일 위치

```
src/main/java/com/ll/framework/ioc/ApplicationContext.java
```

사용 애노테이션:

```
com.ll.framework.ioc.annotations.Component
```

---

## 무엇을 하나요?

1. **컴포넌트 스캔**
   `init()`에서 `basePackage` 하위의 `@Component`가 붙은 클래스를 찾아
   “이름 → 타입” 정의를 `defs` 맵에 등록합니다. (객체는 아직 생성하지 않음)

2. **지연 생성 + 싱글톤**
   `genBean("이름")` 호출 시:

   * 캐시에 객체가 있으면 그대로 반환
   * 없으면 **생성자 주입**으로 새로 만들고 캐시에 저장(싱글톤)

3. **생성자 주입**
   가장 파라미터가 많은 생성자를 선택해, 각 파라미터 **타입**에 맞는 빈을 재귀적으로 주입합니다.

---

## 의존성 (Gradle)

```kotlin
dependencies {
    implementation("org.reflections:reflections:0.10.2")
}
```

---

## 핵심 개념

### 빈 이름 규칙

* `@Component("name")`가 있으면 그 값을 사용
* 없으면 클래스명을 lowerCamelCase로 변환

  * 예: `TestPostService` → `testPostService`

### 싱글톤 보장

* 한 번 생성된 객체는 `singletons` 캐시에 저장되어 **항상 같은 인스턴스**를 반환합니다.

### 타입 주입 방식

* 생성자 파라미터의 **타입**으로 의존성을 찾습니다.
* 등록된 정의(`defs`) 중 `isAssignableFrom`이 참인 첫 번째 타입을 선택합니다.

---

## 코드 훑어보기 (핵심 메서드)

### 1) `init()` — 스캔 & 정의 등록

```java
public void init() {
    Reflections r = new Reflections(basePackage, Scanners.TypesAnnotated);
    for (Class<?> type : r.getTypesAnnotatedWith(Component.class)) {
        defs.put(beanName(type), type); // "이름 → 타입" 등록
    }
}
```

* `Reflections`로 `@Component` 달린 클래스를 모두 수집
* 아직 **객체 생성은 하지 않음** (정의만 저장)

### 2) `genBean(String name)` — 지연 생성 & 싱글톤

```java
@SuppressWarnings("unchecked")
public <T> T genBean(String name) {
    if (singletons.containsKey(name)) return (T) singletons.get(name);

    Class<?> type = defs.get(name);
    if (type == null) throw new NoSuchElementException("No bean named: " + name);

    Object obj = createByConstructor(type); // 생성자 주입
    singletons.put(name, obj);
    return (T) obj;
}
```

* 처음 요청이면 `createByConstructor`로 만들고 캐시에 저장

### 3) `createByConstructor(Class<?> type)` — 생성자 주입

```java
private Object createByConstructor(Class<?> type) {
    try {
        Constructor<?> chosen = Arrays.stream(type.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount)) // 가장 파라미터 많은 생성자
                .orElseThrow();

        Class<?>[] paramTypes = chosen.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            args[i] = getByType(paramTypes[i]); // 파라미터 "타입"으로 빈 주입
        }

        chosen.setAccessible(true);
        return chosen.newInstance(args);
    } catch (Exception e) {
        throw new RuntimeException("Create bean failed: " + type.getName(), e);
    }
}
```

### 4) `getByType(Class<?> need)` — 타입 기반 조회

```java
private Object getByType(Class<?> need) {
    for (Map.Entry<String, Class<?>> e : defs.entrySet()) {
        if (need.isAssignableFrom(e.getValue())) {
            return genBean(e.getKey()); // 이름으로 생성/반환(재귀)
        }
    }
    throw new NoSuchElementException("No bean of type: " + need.getName());
}
```

### 5) `beanName(Class<?> type)` — 이름 생성 규칙

```java
private String beanName(Class<?> type) {
    Component c = type.getAnnotation(Component.class);
    if (c != null && !c.value().isBlank()) return c.value();
    String s = type.getSimpleName();
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
}
```

