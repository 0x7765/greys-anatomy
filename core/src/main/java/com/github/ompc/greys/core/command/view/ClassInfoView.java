package com.github.ompc.greys.core.command.view;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.CodeSource;

import static com.github.ompc.greys.core.util.GaStringUtils.*;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * Java类信息控件
 * Created by vlinux on 15/5/7.
 */
public class ClassInfoView implements View {

    private final Class<?> clazz;
    private final boolean isPrintField;

    public ClassInfoView(Class<?> clazz, boolean isPrintField) {
        this.clazz = clazz;
        this.isPrintField = isPrintField;
    }

    @Override
    public String draw() {
        return drawClassInfo();
    }

    private String getCodeSource(final CodeSource cs) {
        if (null == cs
                || null == cs.getLocation()
                || null == cs.getLocation().getFile()) {
            return EMPTY;
        }

        return cs.getLocation().getFile();
    }

    private String drawClassInfo() {
        final CodeSource cs = clazz.getProtectionDomain().getCodeSource();

        final TableView view = new TableView(new TableView.ColumnDefine[]{
                new TableView.ColumnDefine(TableView.Align.RIGHT),
                new TableView.ColumnDefine(TableView.Align.LEFT)
        })
                .addRow("class-info", tranClassName(clazz))
                .addRow("code-source", getCodeSource(cs))
                .addRow("name", tranClassName(clazz))
                .addRow("isInterface", clazz.isInterface())
                .addRow("isAnnotation", clazz.isAnnotation())
                .addRow("isEnum", clazz.isEnum())
                .addRow("isAnonymousClass", clazz.isAnonymousClass())
                .addRow("isArray", clazz.isArray())
                .addRow("isLocalClass", clazz.isLocalClass())
                .addRow("isMemberClass", clazz.isMemberClass())
                .addRow("isPrimitive", clazz.isPrimitive())
                .addRow("isSynthetic", clazz.isSynthetic())
                .addRow("simple-name", clazz.getSimpleName())
                .addRow("modifier", tranModifier(clazz.getModifiers()))
                .addRow("annotation", drawAnnotation())
                .addRow("interfaces", drawInterface())
                .addRow("super-class", drawSuperClass())
                .addRow("class-loader", drawClassLoader());

        if (isPrintField) {
            view.addRow("fields", drawField());
        }

        return view.hasBorder(true).padding(1).draw();
    }


    private String drawField() {

        final StringBuilder fieldSB = new StringBuilder();

        final Field[] fields = clazz.getDeclaredFields();
        if (null != fields
                && fields.length > 0) {

            for (Field field : fields) {

                final KVView kvView = new KVView(new TableView.ColumnDefine(TableView.Align.RIGHT), new TableView.ColumnDefine(50, false, TableView.Align.LEFT))
                        .add("modifier", tranModifier(field.getModifiers()))
                        .add("type", tranClassName(field.getType()))
                        .add("name", field.getName());


                final StringBuilder annotationSB = new StringBuilder();
                final Annotation[] annotationArray = field.getAnnotations();
                if (null != annotationArray && annotationArray.length > 0) {
                    for (Annotation annotation : annotationArray) {
                        annotationSB.append(tranClassName(annotation.annotationType())).append(",");
                    }
                    if (annotationSB.length() > 0) {
                        annotationSB.deleteCharAt(annotationSB.length() - 1);
                    }
                    kvView.add("annotation", annotationSB);
                }


                if (Modifier.isStatic(field.getModifiers())) {
                    final boolean isAccessible = field.isAccessible();
                    try {
                        field.setAccessible(true);
                        kvView.add("value", newString(field.get(null)));
                    } catch (IllegalAccessException e) {
                        //
                    } finally {
                        field.setAccessible(isAccessible);
                    }
                }//if

                fieldSB.append(kvView.draw()).append("\n");

            }//for

        }

        return fieldSB.toString();
    }

    private String drawAnnotation() {
        final StringBuilder annotationSB = new StringBuilder();
        final Annotation[] annotationArray = clazz.getDeclaredAnnotations();

        if (null != annotationArray && annotationArray.length > 0) {
            for (Annotation annotation : annotationArray) {
                annotationSB.append(tranClassName(annotation.annotationType())).append(",");
            }
            if (annotationSB.length() > 0) {
                annotationSB.deleteCharAt(annotationSB.length() - 1);
            }
        } else {
            annotationSB.append(EMPTY);
        }

        return annotationSB.toString();
    }

    private String drawInterface() {
        final StringBuilder interfaceSB = new StringBuilder();
        final Class<?>[] interfaceArray = clazz.getInterfaces();
        if (null == interfaceArray || interfaceArray.length == 0) {
            interfaceSB.append(EMPTY);
        } else {
            for (Class<?> i : interfaceArray) {
                interfaceSB.append(i.getName()).append(",");
            }
            if (interfaceSB.length() > 0) {
                interfaceSB.deleteCharAt(interfaceSB.length() - 1);
            }
        }
        return interfaceSB.toString();
    }

    private String drawSuperClass() {
        final LadderView ladderView = new LadderView();
        Class<?> superClass = clazz.getSuperclass();
        if (null != superClass) {
            ladderView.addItem(tranClassName(superClass));
            while (true) {
                superClass = superClass.getSuperclass();
                if (null == superClass) {
                    break;
                }
                ladderView.addItem(tranClassName(superClass));
            }//while
        }
        return ladderView.draw();
    }


    private String drawClassLoader() {
        final LadderView ladderView = new LadderView();
        ClassLoader loader = clazz.getClassLoader();
        if (null != loader) {
            ladderView.addItem(loader.toString());
            while (true) {
                loader = loader.getParent();
                if (null == loader) {
                    break;
                }
                ladderView.addItem(loader.toString());
            }
        }
        return ladderView.draw();
    }

}
