// src/main/java/com/ll/framework/ioc/ApplicationContext.java
package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Component;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.reflect.Constructor;
import java.util.*;

import static com.ll.standard.util.Ut.str.lcfirst;

public class ApplicationContext {

    private final String basePackage;
    private final Map<String, Class<?>> defs = new HashMap<>(); //타입 이름 저장
    private final Map<String, Object> singletons = new HashMap<>(); //실제 객체 저장

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    // 스캔해서 등록만 해둔다
    public void init() {
        // Scanners.TypesAnnotated -> annotaion이 붙은 클래스(타입)를 인덱싱하라는 옵션
        Reflections r = new Reflections(basePackage, Scanners.TypesAnnotated);

        //@Component가 붙은 모든 클래스를 반복.
        for (Class<?> type : r.getTypesAnnotatedWith(Component.class)) {

            //객체생성X, 스캔하고 타입의 이름만 등록
            defs.put(lcfirst(type.getSimpleName()), type);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T genBean(String name) {
        if (singletons.containsKey(name)) return (T) singletons.get(name);

        Class<?> type = defs.get(name);

        //타입이 등록도 안되면 에러 반환
        if (type == null) throw new NoSuchElementException("No bean named: " + name);

        Object obj = createByConstructor(type);
        singletons.put(name, obj);
        return (T) obj;
    }

    //파라미터 타입이 들어오면, 그 타입을 만족하는 빈을 찾아 반환.
    private Object getByType(Class<?> need) {
        for (Map.Entry<String, Class<?>> e : defs.entrySet()) {
            if (need.isAssignableFrom(e.getValue())) {
                return genBean(e.getKey()); // 이름으로 생성/반환
            }
        }
        throw new NoSuchElementException("No bean of type: " + need.getName());
    }

    private Object createByConstructor(Class<?> type) {
        try {
            //파라미터가 가장 많은 생성자를 선택
            Constructor<?> chosen = Arrays.stream(type.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount))
                    .orElseThrow();
            Class<?>[] paramTypes = chosen.getParameterTypes();

            // 파라미터가 없으면 그냥 new, 있으면 타입으로 찾아서 넣기
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                args[i] = getByType(paramTypes[i]);
            }

            //private/protected 생성자도 호출 가능하게 접근 허용.
            chosen.setAccessible(true);
            return chosen.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Create bean failed: " + type.getName(), e);
        }
    }

}
