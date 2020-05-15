// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gwtjsonrpc.rebind;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.*;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import com.google.gwtjsonrpc.client.impl.JsonSerializer;
import com.google.gwtjsonrpc.client.impl.ser.*;
import com.google.gwtjsonrpc.client.impl.ser.BoxedPrimitives.*;
import com.google.gwtjsonrpc.common.SkipSerialization;

import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class SerializerCreator {
  private static final String SER_SUFFIX = "_JsonSerializer";
  private static final Comparator<JField> FIELD_COMP =
      new Comparator<JField>() {
        @Override
        public int compare(final JField o1, final JField o2) {
          return o1.getName().compareTo(o2.getName());
        }
      };

  private static final HashMap<String, String> defaultSerializers;
  private static final HashMap<String, String> parameterizedSerializers;

  static {
    defaultSerializers = new HashMap<>();
    parameterizedSerializers = new HashMap<>();

    defaultSerializers.put(
        java.lang.String.class.getCanonicalName(),
        JavaLangString_JsonSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.util.Date.class.getCanonicalName(),
        JavaUtilDate_JsonSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.sql.Date.class.getCanonicalName(),
        JavaSqlDate_JsonSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.sql.Timestamp.class.getCanonicalName(),
        JavaSqlTimestamp_JsonSerializer.class.getCanonicalName());

    //boxed primitive serializers for Lists and maps of boxed primitives
    defaultSerializers.put(
        java.lang.Boolean.class.getCanonicalName(),
        BooleanSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.lang.Byte.class.getCanonicalName(),
        ByteSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.lang.Character.class.getCanonicalName(),
        CharacterSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.lang.Double.class.getCanonicalName(),
        DoubleSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.lang.Float.class.getCanonicalName(),
        FloatSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.lang.Integer.class.getCanonicalName(),
        IntegerSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.lang.Long.class.getCanonicalName(),
        LongSerializer.class.getCanonicalName());
    defaultSerializers.put(
        java.lang.Long.class.getCanonicalName(),
        ShortSerializer.class.getCanonicalName());

    parameterizedSerializers.put(
        java.util.List.class.getCanonicalName(), ListSerializer.class.getCanonicalName());
    parameterizedSerializers.put(
        java.util.ArrayList.class.getCanonicalName(), ListSerializer.class.getCanonicalName());
    parameterizedSerializers.put(
        java.util.Map.class.getCanonicalName(), ObjectMapSerializer.class.getCanonicalName());
    parameterizedSerializers.put(
        java.util.HashMap.class.getCanonicalName(), ObjectMapSerializer.class.getCanonicalName());
    parameterizedSerializers.put(
        java.util.Set.class.getCanonicalName(), SetSerializer.class.getCanonicalName());
    parameterizedSerializers.put(
        java.util.HashSet.class.getCanonicalName(), SetSerializer.class.getCanonicalName());
  }

  private final HashMap<String, String> generatedSerializers;
  private final HashMap<String, String> queuedForGeneration;
  private final Set<String> canSerialize;
  private final GeneratorContext context;
  private JClassType targetType;

  SerializerCreator(final GeneratorContext c) {
    context = c;
    generatedSerializers = new HashMap<>();
    queuedForGeneration = new HashMap<>();
    canSerialize = new HashSet<>();
  }

  String create(JClassType targetType, final TreeLogger logger)
      throws UnableToCompleteException {
    if (targetType.isParameterized() != null || targetType.isArray() != null) {
      ensureSerializersForTypeParameters(logger, targetType);
    }
    String sClassName = serializerFor(targetType);
    if (sClassName != null) {
      return sClassName;
    }
    if (targetType.getQualifiedSourceName().startsWith("com.extjs.gxt.ui.client.data.BeanModel")) {
      //Don't throw an exception, but also don't create a serializer for this class
      return null;
    }
    checkCanSerialize(logger, targetType, true);
    final String sn = getSerializerQualifiedName(targetType);
    String aQSN = getQualifiedSourceName(targetType);

    if (!queuedForGeneration.containsKey(aQSN))
    {
      queuedForGeneration.put(aQSN, sn);
      //logger.log(TreeLogger.WARN, "Create: " + getQualifiedSourceName(targetType));
      recursivelyCreateSerializers(logger, targetType);
    } else
    {
      return sn;
    }

    this.targetType = targetType;
    final SourceWriter srcWriter = getSourceWriter(logger, context);
    if (!generatedSerializers.containsKey(aQSN)) {
      generatedSerializers.put(aQSN, sn);
    }
    if (srcWriter == null) {
      return sn;
    }

    generateSingleton(srcWriter);
    if (targetType.isEnum() != null) {
      generateEnumFromJson(srcWriter);
    } else {
      generateInstanceMembers(srcWriter);
      generatePrintJson(srcWriter, logger);
      generateFromJson(srcWriter, logger);
      generateGetSets(srcWriter);
    }
    srcWriter.commit(logger);
    return sn;
  }

  private void recursivelyCreateSerializers(final TreeLogger logger, final JType targetType)
      throws UnableToCompleteException {
    if (targetType.isPrimitive() != null || isBoxedPrimitive(targetType)) {
      return;
    }

    JClassType targetClass = targetType.isClass();
    final boolean isClass = targetClass != null;
    if (!isClass) {
      targetClass = targetType.isInterface();
    }
    if (isClass && needsSuperSerializer(targetClass)) {
      final TreeLogger branch =
          logger.branch(TreeLogger.DEBUG, "Create Super from " + getQualifiedSourceName(targetType));
      create(targetClass.getSuperclass(), branch);
    }
    for (final JClassType f : getSortedSubTypes(targetClass, logger)) {
      create(f, logger.branch(TreeLogger.DEBUG, "Create Subclass from " + getQualifiedSourceName(targetType)));
    }
    if (isClass) {
      for (final JField f : sortFields(targetClass)) {
        ensureSerializer(logger, f.getType());
      }
    }
  }

  private void ensureSerializer(final TreeLogger logger, final JType type)
      throws UnableToCompleteException {
    if (ensureSerializersForTypeParameters(logger, type)) {
      return;
    }

    if(getQualifiedSourceName(type).equals("java.lang.Object")) {
      return;
    }

    final String qsn = getQualifiedSourceName(type);
    if (defaultSerializers.containsKey(qsn) || parameterizedSerializers.containsKey(type.getQualifiedSourceName())) {
      return;
    }

    create((JClassType) type, logger.branch(TreeLogger.DEBUG, "EnsureSerializer " + getQualifiedSourceName(type)));
  }

  private boolean ensureSerializersForTypeParameters(final TreeLogger logger, final JType type)
      throws UnableToCompleteException {
    if (isJsonPrimitive(type) || isBoxedPrimitive(type)) {
      return true;
    }

    if (type.isArray() != null) {
      ensureSerializer(logger, type.isArray().getComponentType());
      return true;
    }

    if (type.isParameterized() != null) {
      for (final JClassType t : type.isParameterized().getTypeArgs()) {
        ensureSerializer(logger, t);
      }
    }

    return false;
  }

  void checkCanSerialize(final TreeLogger logger, final JType type)
      throws UnableToCompleteException {
    checkCanSerialize(logger, type, true);
  }

  void checkCanSerialize(final TreeLogger logger, final JType type, boolean allowAbstractType)
      throws UnableToCompleteException {
    if (type.getQualifiedSourceName().startsWith("com.extjs.gxt.ui.client.data.BeanModel")) {
      //Don't throw an exception, but also don't create a serializer for this class
      return;
    }
    if (type.isPrimitive() == JPrimitiveType.VOID) {
      logger.log(TreeLogger.ERROR, "Type 'void' not supported in JSON encoding", null);
      throw new UnableToCompleteException();
    }

    final String qsn = getQualifiedSourceName(type);
    if (type.isEnum() != null) {
      return;
    }

    if (isJsonPrimitive(type) || isBoxedPrimitive(type)) {
      return;
    }

    if (type.isArray() != null) {
      final JType leafType = type.isArray().getLeafType();
      if (leafType.isPrimitive() != null || isBoxedPrimitive(leafType)) {
        if (type.isArray().getRank() != 1) {
          logger.log(
              TreeLogger.ERROR,
              "gwtjsonrpc does not support "
                  + "(de)serializing of multi-dimensional arrays of primitves");
          // To work around this, we would need to generate serializers for
          // them, this can be considered a todo
          throw new UnableToCompleteException();
        }
        // Rank 1 arrays work fine.
        return;
      }
      checkCanSerialize(logger, type.isArray().getComponentType());
      return;
    }

    if (defaultSerializers.containsKey(qsn)) {
      return;
    }

    if (type.isParameterized() != null) {
      final JClassType[] typeArgs = type.isParameterized().getTypeArgs();
      for (final JClassType t : typeArgs) {
        // Check for a recursive structure
        if (canSerialize.contains(getQualifiedSourceName(t))) {
          continue;
        }
        final TreeLogger branch =
            logger.branch(TreeLogger.DEBUG, "In type " + qsn + ", ParameterizedType " + t.getName());
        checkCanSerialize(branch, t);
      }
      if (parameterizedSerializers.containsKey(type.getQualifiedSourceName())) {
        return;
      }
    } else if (parameterizedSerializers.containsKey(type.getQualifiedSourceName())) {
      logger.log(TreeLogger.ERROR, "Type " + qsn + " requires type paramter(s)", null);
      throw new UnableToCompleteException();
    }

    if (qsn.equals("java.lang.Object"))
    {
      return;
    }

    if (qsn.startsWith("java.") || qsn.startsWith("javax.")) {
      logger.log(
          TreeLogger.ERROR, "Standard type " + qsn + " not supported in JSON encoding. Target: " + targetType.getName(), null);
      throw new UnableToCompleteException();
    }

    final JClassType ct = (JClassType) type;
    if (ct.isAbstract() && !allowAbstractType) {
      logger.log(TreeLogger.ERROR, "Abstract type " + qsn + " not supported here", null);
      throw new UnableToCompleteException();
    }
    canSerialize.add(qsn);
    for (final JField f : sortFields(ct)) {
      final TreeLogger branch =
          logger.branch(TreeLogger.DEBUG, "In type " + qsn + ", field " + f.getName());
      checkCanSerialize(branch, f.getType());
    }
  }

  String serializerFor(final JType t) {
    if (t.isArray() != null) {
      final JType componentType = t.isArray().getComponentType();
      if (componentType.isPrimitive() != null || isBoxedPrimitive(componentType)) {
        return PrimitiveArraySerializer.class.getCanonicalName();
      }

      return ObjectArraySerializer.class.getCanonicalName()
          + "<"
          + getQualifiedSourceName(componentType)
          + ">";
    }

    if (isStringMap(t)) {
      return StringMapSerializer.class.getName();
    }

    if (isPrimitiveMap(t)) {
      return PrimitiveMapSerializer.class.getName();
    }

    final String qsn = getQualifiedSourceName(t);
    if (defaultSerializers.containsKey(qsn)) {
      return defaultSerializers.get(qsn);
    }

    if (parameterizedSerializers.containsKey(t.getQualifiedSourceName())) {
      return parameterizedSerializers.get(t.getQualifiedSourceName());
    }

    if (queuedForGeneration.containsKey(qsn)) {
      return queuedForGeneration.get(qsn);
    }

    return generatedSerializers.get(qsn);
  }

  private boolean isStringMap(final JType t) {
    return t.isParameterized() != null
        && t.getErasedType().isClassOrInterface() != null
        && t.isParameterized().getTypeArgs().length > 0
        && t.isParameterized()
        .getTypeArgs()[0]
        .getQualifiedSourceName()
        .equals(String.class.getName())
        && t.getErasedType()
        .isClassOrInterface()
        .isAssignableTo(context.getTypeOracle().findType(Map.class.getName()));
  }

  private boolean isPrimitiveMap(final JType t) {
    return t.isParameterized() != null
        && t.getErasedType().isClassOrInterface() != null
        && t.isParameterized().getTypeArgs().length > 0
        && (t.isParameterized().getTypeArgs()[0].isPrimitive() != null
          || t.isParameterized().getTypeArgs()[0].getQualifiedSourceName().equals(String.class.getName())
          || isBoxedPrimitive(t.isParameterized().getTypeArgs()[0]))
        && t.getErasedType()
        .isClassOrInterface()
        .isAssignableTo(context.getTypeOracle().findType(Map.class.getName()));
  }

  private void generateSingleton(final SourceWriter w) {
    w.print("public static final ");
    w.print(getSerializerSimpleName());
    w.print(" INSTANCE = new ");
    w.print(getSerializerSimpleName());
    w.println("();");
    w.println();
  }

  private void generateInstanceMembers(final SourceWriter w) {
    for (final JField f : sortFields(targetType)) {
      final JType ft = f.getType();
      if (needsTypeParameter(ft)) {
        final String serType = serializerFor(ft);
        w.print("private ");
        w.print(serType);
        String aGenericType = getTypedQualifiedSourceName(ft);
        if (aGenericType.indexOf("<") >= 0) {
          aGenericType = aGenericType.substring(aGenericType.indexOf("<"));
          if (serType.equals(StringMapSerializer.class.getName())) {
            aGenericType = "<" + aGenericType.substring(aGenericType.indexOf(", ") + 2, aGenericType.lastIndexOf(">") + 1);
          }
          w.print(aGenericType);
        }
        w.print(" ");
        w.print("ser_" + f.getName());
        w.print(" = ");
        generateSerializerReference(ft, w);
        w.println(";");
      }
    }
    w.println();
  }

  void generateSerializerReference(final JType type, final SourceWriter w) {
    if (type.isArray() != null) {
      final JType componentType = type.isArray().getComponentType();
      if (componentType.isPrimitive() != null || isBoxedPrimitive(componentType)) {
        w.print(PrimitiveArraySerializer.class.getCanonicalName());
        w.print(".INSTANCE");
      } else {
        w.print("new " + serializerFor(type) + "(");
        generateSerializerReference(componentType, w);
        w.print(")");
      }

    } else if (needsTypeParameter(type)) {
      w.print("new " + serializerFor(type) + "(");
      final JClassType[] typeArgs = type.isParameterized().getTypeArgs();
      int n = 0;
      if (isStringMap(type)) {
        n++;
      }
      boolean first = true;
      for (; n < typeArgs.length; n++) {
        if (first) {
          first = false;
        } else {
          w.print(", ");
        }
        generateSerializerReference(typeArgs[n], w);
      }
      w.print(")");

    } else {
        w.print(serializerFor(type) + ".INSTANCE");
    }
  }

  private void generateGetSets(final SourceWriter w) {
    for (final JField f : sortFields(targetType)) {
      if (f.isPrivate()) {
        w.print("private static final native ");

        if (isLong(f.getType())) {
          w.print("java.lang.String");
        } else {
          w.print(getTypedQualifiedSourceName(f.getType()));
        }
        w.print(" objectGet_" + f.getName());
        w.print("(");
        w.print(getQualifiedSourceName(targetType) + " instance");
        w.print(")");
        w.println("/*-{ ");
        w.indent();

        w.print("return instance.@");
        w.print(getQualifiedSourceName(targetType));
        w.print("::");
        w.print(f.getName());
        if (isLong(f.getType())) {
          w.print(" + \"\"");
        }
        w.println(";");

        w.outdent();
        w.println("}-*/;");

        w.print("private static final native void ");
        w.print(" objectSet_" + f.getName());
        w.print("(");
        w.print(getQualifiedSourceName(targetType) + " instance, ");
        w.print(getTypedQualifiedSourceName(f.getType()) + " value");
        w.print(")");
        w.println("/*-{ ");
        w.indent();

        w.print("instance.@");
        w.print(getQualifiedSourceName(targetType));
        w.print("::");
        w.print(f.getName());
        w.println(" = value;");

        w.outdent();
        w.println("}-*/;");
      }

      if (f.getType() == JPrimitiveType.CHAR || isBoxedCharacter(f.getType())) {
        w.print("private static final native String");
        w.print(" jsonGet0_" + f.getName());
        w.print("(final JavaScriptObject instance)");
        w.println("/*-{ ");
        w.indent();
        w.print("return instance.");
        w.print(f.getName());
        w.println(";");
        w.outdent();
        w.println("}-*/;");

        w.print("private static final ");
        w.print(f.getType() == JPrimitiveType.CHAR ? "char" : "Character");
        w.print(" jsonGet_" + f.getName());
        w.print("(JavaScriptObject instance)");
        w.println(" {");
        w.indent();
        w.print("return ");
        w.print(JsonSerializer.class.getName());
        w.print(".toChar(");
        w.print("jsonGet0_" + f.getName());
        w.print("(instance)");
        w.println(");");
        w.outdent();
        w.println("}");
      } else {
        w.print("private static final native ");
        if (isLong(f.getType())) {
          w.print("java.lang.String");
        } else if (f.getType().isArray() != null) {
          w.print("JavaScriptObject");
        } else if (isJsonPrimitive(f.getType())) {
          w.print(getQualifiedSourceName(f.getType()));
        } else if (isBoxedPrimitive(f.getType())) {
          w.print(boxedTypeToPrimitiveTypeName(f.getType()));
        } else {
          w.print("Object");
        }
        w.print(" jsonGet_" + f.getName());
        w.print("(JavaScriptObject instance)");
        w.println("/*-{ ");
        w.indent();

        w.print("return instance.");
        w.print(getPrettyFieldName(f.getName()));
        if (isLong(f.getType())) {
          w.print(" + \"\"");
        }
        w.println(";");

        w.outdent();
        w.println("}-*/;");
      }

      w.println();
    }
  }

  private void generateEnumFromJson(final SourceWriter w) {
    w.print("public ");
    w.print(getQualifiedSourceName(targetType));
    w.println(" fromJson(Object in) {");
    w.indent();
    w.print("return in != null");
    w.print(" ? " + getQualifiedSourceName(targetType) + ".valueOf((String)in)");
    w.print(" : null");
    w.println(";");
    w.outdent();
    w.println("}");
    w.println();
  }

  private void generatePrintJson(final SourceWriter w, TreeLogger theLogger) {
    final JField[] fieldList = sortFields(targetType);
    w.print("protected int printJsonImpl(int fieldCount, StringBuilder sb, ");
    w.println("Object instance) {");
    w.indent();
    w.println("return printTypeCheckedJsonImpl(fieldCount, sb, instance, true);");
    w.outdent();
    w.println("}");

    w.print("public int printTypeCheckedJsonImpl(int fieldCount, StringBuilder sb, ");
    w.println("Object instance, boolean doCheckType) {");
    w.indent();

    w.println("if (doCheckType) {");
    w.indent();
    List<JClassType> aSubTypes = getSubTypes(targetType, theLogger);
    if (aSubTypes.size() > 0 )
    {
      w.println("switch (instance.getClass().getName()){");
      w.indent();
      aSubTypes.forEach(aSubClass -> {
        w.println("case \"" + getQualifiedSourceName(aSubClass) + "\" : return " + getSerializerQualifiedName(aSubClass) + ".INSTANCE.printTypeCheckedJsonImpl(fieldCount, sb, instance, false);");
      });
      w.outdent();
      w.println("}");
      w.println();
    }
    w.outdent();
    w.println("}");

    w.print("final ");
    w.print(getTypedQualifiedSourceName(targetType));
    w.print(" src = (");
    w.print(getTypedQualifiedSourceName(targetType));
    w.println(")instance;");

    if (needsSuperSerializer(targetType)) {
      w.print("fieldCount = super.printTypeCheckedJsonImpl(fieldCount, sb, (");
      w.print(getTypedQualifiedSourceName(targetType.getSuperclass()));
      w.println(")src, false);");
    }

    final String docomma = "if (fieldCount++ > 0) sb.append(\",\");";
    for (final JField f : fieldList) {
      final String doget;
      if (f.isPrivate()) {
        doget = "objectGet_" + f.getName() + "(src)";
      } else {
        doget = "src." + f.getName();
      }

      final String doname = "sb.append(\"\\\"" + getPrettyFieldName(f.getName()) + "\\\":\");";
      if (f.getType() == JPrimitiveType.CHAR || isBoxedCharacter(f.getType())) {
        w.println(docomma);
        w.println(doname);
        w.println("sb.append(" + JsonUtils.class.getSimpleName());
        w.println(".escapeValue(String.valueOf(" + doget + ")));");
      } else if (isJsonString(f.getType())) {
        w.println("if (" + doget + " != null) {");
        w.indent();
        w.println(docomma);
        w.println(doname);
        w.println("sb.append(" + JsonUtils.class.getSimpleName() + ".escapeValue(" + doget + "));");
        w.outdent();
        w.println("}");
        w.println();
      } else if (isJsonPrimitive(f.getType()) || isBoxedPrimitive(f.getType())) {
        w.println(docomma);
        w.println(doname);
        w.println("sb.append(" + doget + ");");
        w.println();
      } else {
        w.println("if (" + doget + " != null) {");
        w.indent();
        w.println(docomma);
        w.println(doname);
        if (needsTypeParameter(f.getType())) {
          w.println("try{");
          w.indent();
          w.print("ser_" + f.getName());
          w.println(".printJson(sb, " + doget + ");");
          w.outdent();
          w.println("} catch(Exception anE){");
          w.indent();
          w.print("ser_" + f.getName() + " = ");
          generateSerializerReference(f.getType(), w);
          w.println(";");
          w.print("ser_" + f.getName());
          w.println(".printJson(sb, " + doget + ");");
          w.outdent();
          w.println("}");
        } else {
          w.print(serializerFor(f.getType()) + ".INSTANCE");
          w.println(".printJson(sb, " + doget + ");");
        }
        w.outdent();
        w.println("}");
        w.println();
      }
    }

    w.println("return fieldCount;");
    w.outdent();
    w.println("}");
    w.println();
  }

  private void generateFromJson(final SourceWriter w, TreeLogger theLogger) {
    w.print("public ");
    w.print(getTypedQualifiedSourceName(targetType));
    w.println(" fromJson(Object in) {");
    w.indent();

    w.println("if (in == null) return null;");
    w.println("final JavaScriptObject jso = (JavaScriptObject)in;");
    w.print("final ");
    w.print(getTypedQualifiedSourceName(targetType));
    if (targetType.isAbstract() || targetType.isInterface() != null) {
      w.println(" dst = null; ");
    } else {
      w.print(" dst = new ");
      w.println(getTypedQualifiedSourceName(targetType) + "();");
    }
    w.println("return fromJsonImpl(jso, dst);");

    w.outdent();
    w.println("}");
    w.println();

    w.print("public " + getTypedQualifiedSourceName(targetType) + " fromJsonImpl(JavaScriptObject jso, ");
    w.print(getTypedQualifiedSourceName(targetType));
    w.println(" dst) {");
    w.indent();
    w.println("return fromJsonImpl(jso, dst, true);");
    w.outdent();
    w.println("}");
    w.println();

    // this is the only part thats really MB specific, but its kinda the foundation we need to do this fromJson
    // when were creating an object from JSON we need the type to determine what kind of object it should be
    w.println("static native String objectType(JavaScriptObject responseObject)");
    w.println("/*-{ return responseObject._type_; }-*/;");
    w.println();

    w.print("public " + getTypedQualifiedSourceName(targetType) + " fromJsonImpl(JavaScriptObject jso, ");
    w.print(getTypedQualifiedSourceName(targetType));
    w.println(" dst, boolean doCheckType) {");
    w.indent();

    w.println("if (doCheckType) {");
    w.indent();
    List<JClassType> aSubTypes = getSubTypes(targetType, theLogger);
    if (aSubTypes.size() > 0 )
    {
      w.println("switch (objectType(jso)){");
      w.indent();
      aSubTypes.forEach(aSubClass -> {
        w.print("case \"" + getQualifiedSourceName(aSubClass) + "\" : dst = ");
        w.print(getSerializerQualifiedName(aSubClass) + ".INSTANCE.fromJsonImpl(jso, ");
        w.print("new " + getQualifiedSourceName(aSubClass));
        w.print("(), false);");
        w.println("break;");
      });
      w.outdent();
      w.println("}");
    }
    w.outdent();
    w.println("}");

    if (needsSuperSerializer(targetType)) {
      w.print("super.fromJsonImpl(jso, (");
      w.print(getTypedQualifiedSourceName(targetType.getSuperclass()));
      w.println(")dst, false);");
    }

    for (final JField f : sortFields(targetType)) {
      final String doget = "jsonGet_" + f.getName() + "(jso)";
      final String doset0, doset1;

      if (f.getType().isArray() != null || needsTypeParameter(f.getType()))
      {
        w.println("try{");
        w.indent();
        writeFieldSetter(w, f);
        w.outdent();
        w.println("} catch(Exception anE){");
        w.indent();
        w.print("ser_" + f.getName() + " = ");
        generateSerializerReference(f.getType(), w);
        w.println(";");
        writeFieldSetter(w, f);
        w.outdent();
        w.println("}");
      } else {
        writeFieldSetter(w, f);
      }
    }

    w.println("return dst;");
    w.outdent();
    w.println("}");
    w.println();
  }

  private void writeFieldSetter(final SourceWriter w, JField f)
  {
    final String doget = "jsonGet_" + f.getName() + "(jso)";
    final String doset0, doset1;

    if (f.isPrivate()) {
      doset0 = "objectSet_" + f.getName() + "(dst, ";
      doset1 = ")";
    } else {
      doset0 = "dst." +f.getName() + " = ";
      doset1 = "";
    }
    if (f.getType().isArray() != null) {
      final JType ct = f.getType().isArray().getComponentType();
      w.println("if (" + doget + " != null) {");
      w.indent();

      w.print("final ");
      w.print(getQualifiedSourceName(ct));
      w.print("[] tmp = new ");
      w.print(getQualifiedSourceName(ct));
      w.print("[");
      w.print(ObjectArraySerializer.class.getName());
      w.print(".size(" + doget + ")");
      w.println("];");

      w.println("ser_" + f.getName() + ".fromJson(" + doget + ", tmp);");

      w.print(doset0);
      w.print("tmp");
      w.print(doset1);
      w.println(";");

      w.outdent();
      w.println("}");

    } else if (isJsonPrimitive(f.getType())) {
      w.print(doset0);
      if (isLong(f.getType())) {
        w.print("Long.parseLong(");
        w.print(doget);
        w.print(")");
      } else {
        w.print(doget);
      }
      w.print(doset1);
      w.println(";");

    } else if (isBoxedPrimitive(f.getType())) {
      w.print(doset0);
      if (isLong(f.getType())) {
        w.print("Long.parseLong(");
      } else {
        w.print("new " + getQualifiedSourceName(f.getType()) + "(");
      }
      w.print(doget);
      w.print(")");
      w.print(doset1);
      w.println(";");

    } else {
      w.print(doset0);
      if (needsTypeParameter(f.getType())) {
        if (f.getType().isInterface() == null)
        {
          w.print("(" + getTypedQualifiedSourceName(f.getType()) + ")");
        }
        w.print("ser_" + f.getName());
      } else {
        w.print(serializerFor(f.getType()) + ".INSTANCE");
      }
      w.print(".fromJson(" + doget + ")");
      w.print(doset1);
      w.println(";");
    }
  }

  static boolean isJsonPrimitive(final JType t) {
    return t.isPrimitive() != null || isJsonString(t);
  }

  static boolean isBoxedPrimitive(final JType t) {
    final String qsn = t.getQualifiedSourceName();
    return qsn.equals(Boolean.class.getCanonicalName())
        || qsn.equals(Byte.class.getCanonicalName())
        || isBoxedCharacter(t)
        || qsn.equals(Double.class.getCanonicalName())
        || qsn.equals(Long.class.getCanonicalName())
        || qsn.equals(Float.class.getCanonicalName())
        || qsn.equals(Integer.class.getCanonicalName())
        || qsn.equals(Short.class.getCanonicalName());
  }

  static boolean isBoxedCharacter(JType t) {
    return t.getQualifiedSourceName().equals(Character.class.getCanonicalName());
  }

  static boolean isLong(JType t) {
    return t == JPrimitiveType.LONG || t.getQualifiedSourceName().equals(Long.class.getCanonicalName());
  }

  private String boxedTypeToPrimitiveTypeName(JType t) {
    final String qsn = t.getQualifiedSourceName();
    if (qsn.equals(Boolean.class.getCanonicalName())) return "boolean";
    if (qsn.equals(Byte.class.getCanonicalName())) return "byte";
    if (qsn.equals(Character.class.getCanonicalName())) return "java.lang.String";
    if (qsn.equals(Double.class.getCanonicalName())) return "double";
    if (qsn.equals(Long.class.getCanonicalName())) return "java.lang.String";
    if (qsn.equals(Float.class.getCanonicalName())) return "float";
    if (qsn.equals(Integer.class.getCanonicalName())) return "int";
    if (qsn.equals(Short.class.getCanonicalName())) return "short";
    throw new IllegalArgumentException(t + " is not a boxed type");
  }

  static boolean isJsonString(final JType t) {
    return t.getQualifiedSourceName().equals(String.class.getCanonicalName());
  }

  private SourceWriter getSourceWriter(final TreeLogger logger, final GeneratorContext ctx) {
    final JPackage targetPkg = targetType.getPackage();
    final String pkgName = targetPkg == null ? "" : targetPkg.getName();
    final PrintWriter pw;
    final ClassSourceFileComposerFactory cf;

    pw = ctx.tryCreate(logger, pkgName, getSerializerSimpleName());
    if (pw == null) {
      return null;
    }

    cf = new ClassSourceFileComposerFactory(pkgName, getSerializerSimpleName());
    cf.addImport(JavaScriptObject.class.getCanonicalName());
    cf.addImport(JsonSerializer.class.getCanonicalName());
    cf.addImport(JsonUtils.class.getCanonicalName());
    if (targetType.isEnum() != null) {
      cf.addImport(EnumSerializer.class.getCanonicalName());
      cf.setSuperclass(
          EnumSerializer.class.getSimpleName() + "<" + getQualifiedSourceName(targetType) + ">");
    } else if (needsSuperSerializer(targetType)) {
      cf.setSuperclass(getSerializerQualifiedName(targetType.getSuperclass()));
    } else {
      cf.addImport(ObjectSerializer.class.getCanonicalName());
      cf.setSuperclass(
          ObjectSerializer.class.getSimpleName() + "<" + getTypedQualifiedSourceName(targetType) + ">");
    }
    return cf.createSourceWriter(ctx, pw);
  }

  private static boolean needsSuperSerializer(JClassType type) {
    JClassType t = type.getSuperclass();
    while (t != null && !Object.class.getName().equals(getQualifiedSourceName(t))) {
      if (sortFields(t).length > 0) {
        return true;
      }
      t = t.getSuperclass();
    }
    return false;
  }

  private String getSerializerQualifiedName(final JClassType theTargetType) {
    final String[] name;
    name = ProxyCreator.synthesizeTopLevelClassName(theTargetType, SER_SUFFIX);
    String aName = name[0].length() == 0 ? name[1] : name[0] + "." + name[1];
    int anExtends = aName.indexOf(" extends ");
    return anExtends >= 0 ? aName.substring(anExtends + " extends ".length()) : aName;
  }

  private String getSerializerSimpleName() {
    return ProxyCreator.synthesizeTopLevelClassName(targetType, SER_SUFFIX)[1];
  }

  static boolean needsTypeParameter(final JType ft) {
    return ft.isArray() != null
        || (ft.isParameterized() != null
        && parameterizedSerializers.containsKey(ft.getQualifiedSourceName()));
  }

  private static JField[] sortFields(final JClassType targetType) {
    final ArrayList<JField> r = new ArrayList<>();
    for (final JField f : targetType.getFields()) {
      if (f.isAnnotationPresent(SkipSerialization.class)) {
        continue;
      }
      if (!f.isStatic() && !f.isTransient() && !f.isFinal()) {
        r.add(f);
      }
    }
    Collections.sort(r, FIELD_COMP);
    return r.toArray(new JField[r.size()]);
  }

  private final List<JClassType>getSubTypes(final JClassType targetType, TreeLogger theLogger) {
    String anOriginal = getSortedSubTypes(targetType, theLogger).stream()
        .map(aSubType -> aSubType.getName())
        .reduce((a,b) -> a + ", " + b).orElse("none");
    //theLogger.log(TreeLogger.WARN, targetType.getName() + " All " + anOriginal);
    return getSortedSubTypes(targetType, theLogger).stream()
        .filter(aSubType -> !(aSubType.isAbstract()
            || aSubType.isInterface() != null
            || aSubType.getQualifiedSourceName().startsWith("com.extjs.gxt")))
        .collect(Collectors.toList());
  }

  private final Set<JClassType>getSortedSubTypes(JClassType targetType, TreeLogger theLogger) {
    JClassType aRefreshed = context.getTypeOracle().findType(targetType.getPackage().getName(), targetType.getName());
    //theLogger.log(TreeLogger.WARN, targetType.getQualifiedSourceName() + " -> " + (aRefreshed != null ? aRefreshed.getQualifiedSourceName() : null));
    targetType = aRefreshed != null ? aRefreshed : targetType;
    //theLogger.log(TreeLogger.WARN, "getSortedSubTypes:qsn:" + targetType.getQualifiedSourceName());
    String anImmediateSubTypeOf = targetType.getQualifiedSourceName().indexOf(" extends ") > 0 ? getQualifiedSourceName(targetType) : targetType.getQualifiedSourceName();

    //Add all ImmediateSubTypes First
    Set<JClassType> aSortedSubTypes = new HashSet();
    aSortedSubTypes.addAll(Arrays.asList(targetType.getSubtypes()).stream()
        .filter(aJClassType -> {
          try
          {
            return aJClassType.getSuperclass().getQualifiedSourceName().equals(anImmediateSubTypeOf);
          } catch (Exception anE) {
            return false;
          }
        })
        .collect(Collectors.toList()));
    aSortedSubTypes.addAll(Arrays.asList(targetType.getSubtypes()));
    return aSortedSubTypes;
  }

  public static String getQualifiedSourceName(JType theType) {
    String aQSN = theType.getQualifiedSourceName();
    int anExtends = aQSN.indexOf(" extends ");
    String aFixed = anExtends >= 0 ? aQSN.substring(anExtends + " extends ".length()) : aQSN;

    JParameterizedType isGeneric = theType.isParameterized();
    JClassType aClass = theType.isClassOrInterface();
    if (isGeneric != null && !aClass.isAbstract() && aClass.isInterface() == null) {
      for (JClassType param : isGeneric.getTypeArgs()) {
        aFixed += "_";
        aFixed += param.getQualifiedSourceName().replace('.', '_');
      }
    }
    return aFixed;
  }

  public static String getTypedQualifiedSourceName(JType theType) {
    String aQSN = theType.getQualifiedSourceName();
    int anExtends = aQSN.indexOf(" extends ");
    String aFixed = anExtends >= 0 ? aQSN.substring(anExtends + " extends ".length()) : aQSN;

    JParameterizedType isGeneric = theType.isParameterized();
    JClassType aClass = theType.isClassOrInterface();
    if (isGeneric != null && !aClass.isAbstract() && aClass.isInterface() == null) {
      aFixed += getTypeWithDefinedGenerics(theType);
    }
    return aFixed;
  }

  private static String getTypeWithDefinedGenerics(JType theType) {
    return "<" +
        Arrays.asList(theType.isParameterized().getTypeArgs()).stream()
        .map(aType -> {
          String aParameters = aType.getQualifiedSourceName();
          JClassType aClass = theType.isClassOrInterface();
          if (aType.isParameterized() != null && !aClass.isAbstract() && aClass.isInterface() == null)
          {
            aParameters += getTypeWithDefinedGenerics(aType);
          }
          return aParameters;
        })
        .reduce((a, b) -> a + ", " + b)
        .orElse("")
        + ">";
  }

  private static Pattern kPropertyPattern = Pattern.compile("^(my([A-Z])).*");

  /**
   * MediaBeacon used the variable name prefix 'my' to indicate the variable is a global to that object
   * we should simplify this so our rpc calls look prettier
   * @param theName
   * @return
   */
  private static String getPrettyFieldName(String theName) {
    Matcher aPropertyNameMatcher = kPropertyPattern.matcher(theName);
    if (!aPropertyNameMatcher.matches())
    {
      return theName;
    }
    return theName.replace(aPropertyNameMatcher.group(1), aPropertyNameMatcher.group(2).toLowerCase());
  }
}
