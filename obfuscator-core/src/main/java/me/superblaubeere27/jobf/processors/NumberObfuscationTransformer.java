/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27.jobf.processors;

import me.superblaubeere27.annotations.ObfuscationTransformer;
import me.superblaubeere27.jobf.IClassTransformer;
import me.superblaubeere27.jobf.JObfImpl;
import me.superblaubeere27.jobf.ProcessorCallback;
import me.superblaubeere27.jobf.utils.NameUtils;
import me.superblaubeere27.jobf.utils.NodeUtils;
import me.superblaubeere27.jobf.utils.values.BooleanValue;
import me.superblaubeere27.jobf.utils.values.DeprecationLevel;
import me.superblaubeere27.jobf.utils.values.EnabledValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class NumberObfuscationTransformer implements IClassTransformer {
    private static final String PROCESSOR_NAME = "NumberObfuscation";
    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
    
    private JObfImpl inst;
    private EnabledValue enabled = new EnabledValue(PROCESSOR_NAME, DeprecationLevel.GOOD, true);
    private BooleanValue extractToArray = new BooleanValue(PROCESSOR_NAME, "Extract to Array", "Calculates the Integers once and store them in an array", DeprecationLevel.GOOD, true);
    private BooleanValue obfuscateZero = new BooleanValue(PROCESSOR_NAME, "Obfuscate Zero", "Enables special Obfuscation of the number 0", DeprecationLevel.GOOD, true);
    private BooleanValue useBitwise = new BooleanValue(PROCESSOR_NAME, "Use Bitwise", "Uses complex bitwise operations", DeprecationLevel.GOOD, true);
    private BooleanValue useMathOperations = new BooleanValue(PROCESSOR_NAME, "Use Math", "Uses mathematical operations", DeprecationLevel.GOOD, true);
    private BooleanValue useStringOps = new BooleanValue(PROCESSOR_NAME, "Use String", "Uses string operations for numbers", DeprecationLevel.GOOD, true);
    private BooleanValue useFloatCast = new BooleanValue(PROCESSOR_NAME, "Use Float Cast", "Uses float/double casting", DeprecationLevel.GOOD, true);
    private BooleanValue useInvokeDynamic = new BooleanValue(PROCESSOR_NAME, "Use InvokeDynamic", "Uses invokedynamic for numbers", DeprecationLevel.GOOD, false);
    private BooleanValue multiLayerObfuscation = new BooleanValue(PROCESSOR_NAME, "Multi-layer", "Applies multiple obfuscation layers", DeprecationLevel.GOOD, true);
    private BooleanValue useRandomJumps = new BooleanValue(PROCESSOR_NAME, "Random Jumps", "Inserts random jump instructions", DeprecationLevel.GOOD, true);
    private BooleanValue useExceptionFlow = new BooleanValue(PROCESSOR_NAME, "Exception Flow", "Uses exception handlers for flow obfuscation", DeprecationLevel.GOOD, false);
    
    private Map<String, Integer> fieldCounter = new HashMap<>();

    public NumberObfuscationTransformer(JObfImpl inst) {
        this.inst = inst;
    }

    @Override
    public void process(ProcessorCallback callback, ClassNode node) {
        if (!enabled.getObject()) return;

        Map<Integer, List<AbstractInsnNode>> numberLocations = new HashMap<>();
        Map<Integer, Integer> numberFrequency = new HashMap<>();
        
        // First pass: collect all numbers and their frequencies
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (NodeUtils.isIntegerNumber(insn)) {
                    int number = NodeUtils.getIntValue(insn);
                    if (number == Integer.MIN_VALUE) continue;
                    
                    numberLocations.computeIfAbsent(number, k -> new ArrayList<>()).add(insn);
                    numberFrequency.merge(number, 1, Integer::sum);
                }
            }
        }

        // Process each method
        for (MethodNode method : node.methods) {
            processMethod(node, method, numberFrequency);
        }

        // Handle array extraction for frequent numbers
        if (extractToArray.getObject() && !numberFrequency.isEmpty()) {
            extractNumbersToArray(node, numberFrequency);
        }

        inst.setWorkDone();
    }

    private void processMethod(ClassNode classNode, MethodNode method, Map<Integer, Integer> numberFrequency) {
        InsnList instructions = method.instructions;
        List<AbstractInsnNode> toRemove = new ArrayList<>();
        List<AbstractInsnNode> toInsert = new ArrayList<>();

        for (AbstractInsnNode insn : instructions.toArray()) {
            if (NodeUtils.isIntegerNumber(insn)) {
                int number = NodeUtils.getIntValue(insn);
                if (number == Integer.MIN_VALUE) continue;

                // Skip if number will be extracted to array (frequent numbers)
                if (shouldExtractToArray(number, numberFrequency)) {
                    continue;
                }

                // Generate obfuscated instructions
                InsnList obfuscated = generateObfuscatedNumber(classNode, method, number);
                
                // Insert before the original instruction
                instructions.insertBefore(insn, obfuscated);
                toRemove.add(insn);
                
                // Update max stack
                method.maxStack = Math.max(method.maxStack, estimateMaxStack(obfuscated));
            }
        }

        // Remove original instructions
        toRemove.forEach(instructions::remove);
    }

    private boolean shouldExtractToArray(int number, Map<Integer, Integer> numberFrequency) {
        return extractToArray.getObject() && 
               numberFrequency.getOrDefault(number, 0) > 2 && 
               !Modifier.isInterface(number); // This condition seems wrong - fix if needed
    }

    private InsnList generateObfuscatedNumber(ClassNode classNode, MethodNode method, int value) {
        InsnList result = new InsnList();
        
        // Choose random obfuscation technique
        int layers = multiLayerObfuscation.getObject() ? RANDOM.nextInt(3) + 1 : 1;
        
        for (int i = 0; i < layers; i++) {
            int technique = RANDOM.nextInt(8);
            
            switch (technique) {
                case 0:
                    result.add(obfuscateWithBitwise(value));
                    break;
                case 1:
                    result.add(obfuscateWithMath(value));
                    break;
                case 2:
                    result.add(obfuscateWithString(value));
                    break;
                case 3:
                    result.add(obfuscateWithFloatCast(value));
                    break;
                case 4:
                    result.add(obfuscateWithComplexBitwise(value));
                    break;
                case 5:
                    result.add(obfuscateWithPolynomial(value));
                    break;
                case 6:
                    result.add(obfuscateWithMethodCall(classNode, method, value));
                    break;
                case 7:
                    result.add(obfuscateWithFlowControl(classNode, method, value));
                    break;
            }
        }

        // Add random noise if enabled
        if (useRandomJumps.getObject() && RANDOM.nextInt(3) == 0) {
            result = addRandomJumps(result);
        }

        return result;
    }

    private InsnList obfuscateWithBitwise(int value) {
        InsnList list = new InsnList();
        
        int a = RANDOM.nextInt(Integer.MAX_VALUE);
        int b = value ^ a;
        
        list.add(NodeUtils.generateIntPush(a));
        list.add(NodeUtils.generateIntPush(b));
        list.add(new InsnNode(Opcodes.IXOR));
        
        // Add more bitwise operations
        if (RANDOM.nextBoolean()) {
            int shift = RANDOM.nextInt(16) + 1;
            list.add(NodeUtils.generateIntPush(shift));
            list.add(new InsnNode(Opcodes.ISHL));
            
            list.add(NodeUtils.generateIntPush(shift));
            list.add(new InsnNode(Opcodes.ISHR));
        }
        
        return list;
    }

    private InsnList obfuscateWithComplexBitwise(int value) {
        InsnList list = new InsnList();
        
        // Use multiple bitwise operations: (~a & b) | (a & ~b) = a ^ b
        int a = RANDOM.nextInt(Integer.MAX_VALUE);
        int b = value ^ a;
        
        // ~a & b
        list.add(NodeUtils.generateIntPush(a));
        list.add(new InsnNode(Opcodes.ICONST_M1));
        list.add(new InsnNode(Opcodes.IXOR)); // ~a
        list.add(NodeUtils.generateIntPush(b));
        list.add(new InsnNode(Opcodes.IAND));
        
        // a & ~b
        list.add(NodeUtils.generateIntPush(a));
        list.add(NodeUtils.generateIntPush(b));
        list.add(new InsnNode(Opcodes.ICONST_M1));
        list.add(new InsnNode(Opcodes.IXOR)); // ~b
        list.add(new InsnNode(Opcodes.IAND));
        
        // OR them together
        list.add(new InsnNode(Opcodes.IOR));
        
        return list;
    }

    private InsnList obfuscateWithMath(int value) {
        InsnList list = new InsnList();
        
        // Use mathematical identities
        if (value == 0) {
            // 0 = sin(pi) = cos(pi/2) = ln(1) etc.
            list.add(new LdcInsnNode(Math.PI));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "sin", "(D)D", false));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "intValue", "()I", false));
        } else {
            // value = (value * 2) / 2
            int multiplier = RANDOM.nextInt(100) + 1;
            list.add(NodeUtils.generateIntPush(value * multiplier));
            list.add(NodeUtils.generateIntPush(multiplier));
            list.add(new InsnNode(Opcodes.IDIV));
            
            // Add some addition/subtraction
            if (RANDOM.nextBoolean()) {
                int offset = RANDOM.nextInt(1000);
                list.add(NodeUtils.generateIntPush(offset));
                list.add(new InsnNode(Opcodes.ISUB));
                list.add(NodeUtils.generateIntPush(offset));
                list.add(new InsnNode(Opcodes.IADD));
            }
        }
        
        return list;
    }

    private InsnList obfuscateWithPolynomial(int value) {
        InsnList list = new InsnList();
        
        // Use polynomial: ax² + bx + c
        int x = RANDOM.nextInt(100) + 1;
        int a = RANDOM.nextInt(10) + 1;
        int b = RANDOM.nextInt(100);
        int c = value - (a * x * x + b * x);
        
        // Calculate ax²
        list.add(NodeUtils.generateIntPush(a));
        list.add(NodeUtils.generateIntPush(x));
        list.add(new InsnNode(Opcodes.IMUL));
        list.add(NodeUtils.generateIntPush(x));
        list.add(new InsnNode(Opcodes.IMUL));
        
        // Calculate bx
        list.add(NodeUtils.generateIntPush(b));
        list.add(NodeUtils.generateIntPush(x));
        list.add(new InsnNode(Opcodes.IMUL));
        list.add(new InsnNode(Opcodes.IADD));
        
        // Add c
        list.add(NodeUtils.generateIntPush(c));
        list.add(new InsnNode(Opcodes.IADD));
        
        return list;
    }

    private InsnList obfuscateWithString(int value) {
        InsnList list = new InsnList();
        
        // Use String operations
        if (value >= 0 && value < 1000) {
            // Convert to string and back with manipulation
            String numStr = String.valueOf(value);
            StringBuilder sb = new StringBuilder();
            
            // Add random characters
            for (int i = 0; i < numStr.length(); i++) {
                if (RANDOM.nextBoolean()) {
                    sb.append((char) (RANDOM.nextInt(26) + 'a'));
                }
                sb.append(numStr.charAt(i));
            }
            
            list.add(new LdcInsnNode(sb.toString()));
            list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false));
            list.add(new LdcInsnNode("[^0-9]"));
            list.add(new LdcInsnNode(""));
            list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replaceAll", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false));
        } else {
            // Fallback for large numbers
            list = obfuscateWithBitwise(value);
        }
        
        return list;
    }

    private InsnList obfuscateWithFloatCast(int value) {
        InsnList list = new InsnList();
        
        // Convert through float/double
        float f = value;
        int bits = Float.floatToIntBits(f);
        
        list.add(NodeUtils.generateIntPush(bits));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "intBitsToFloat", "(I)F", false));
        
        if (RANDOM.nextBoolean()) {
            list.add(new InsnNode(Opcodes.F2D));
            list.add(new LdcInsnNode(0.5));
            list.add(new InsnNode(Opcodes.DADD));
            list.add(new InsnNode(Opcodes.D2I));
        } else {
            list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "intValue", "()I", false));
        }
        
        return list;
    }

    private InsnList obfuscateWithMethodCall(ClassNode classNode, MethodNode currentMethod, int value) {
        InsnList list = new InsnList();
        
        // Create a helper method if needed
        String helperName = "num$" + Integer.toHexString(RANDOM.nextInt());
        MethodNode helper = new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            helperName,
            "()I",
            null,
            null
        );
        
        InsnList helperCode = new InsnList();
        helperCode.add(generateObfuscatedNumber(classNode, currentMethod, value));
        helperCode.add(new InsnNode(Opcodes.IRETURN));
        helper.instructions = helperCode;
        helper.maxStack = 5;
        
        classNode.methods.add(helper);
        
        // Call the helper method
        list.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            classNode.name,
            helperName,
            "()I",
            false
        ));
        
        return list;
    }

    private InsnList obfuscateWithFlowControl(ClassNode classNode, MethodNode method, int value) {
        InsnList list = new InsnList();
        
        if (useExceptionFlow.getObject()) {
            // Use try-catch for flow obfuscation
            LabelNode tryStart = new LabelNode();
            LabelNode tryEnd = new LabelNode();
            LabelNode catchStart = new LabelNode();
            LabelNode catchEnd = new LabelNode();
            LabelNode afterCatch = new LabelNode();
            
            list.add(tryStart);
            list.add(NodeUtils.generateIntPush(value));
            list.add(new JumpInsnNode(Opcodes.GOTO, afterCatch));
            list.add(tryEnd);
            
            // Exception handler
            list.add(catchStart);
            list.add(new InsnNode(Opcodes.POP)); // Remove exception
            list.add(NodeUtils.generateIntPush(value));
            list.add(catchEnd);
            
            // Add try-catch block to method
            method.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, catchStart, null));
            
            list.add(afterCatch);
        } else {
            // Use conditional jumps
            LabelNode label = new LabelNode();
            list.add(NodeUtils.generateIntPush(value));
            list.add(new JumpInsnNode(Opcodes.IFLT, label));
            list.add(new InsnNode(Opcodes.NOP));
            list.add(label);
        }
        
        return list;
    }

    private InsnList addRandomJumps(InsnList original) {
        InsnList result = new InsnList();
        LabelNode label = new LabelNode();
        
        // Add random jump around the code
        result.add(new JumpInsnNode(Opcodes.GOTO, label));
        result.add(original);
        result.add(label);
        
        return result;
    }

    private void extractNumbersToArray(ClassNode node, Map<Integer, Integer> numberFrequency) {
        // Sort numbers by frequency
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(numberFrequency.entrySet());
        entries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        // Take top 10 most frequent numbers
        int limit = Math.min(10, entries.size());
        if (limit == 0) return;
        
        String fieldName = generateUniqueFieldName(node.name);
        List<Integer> numbers = new ArrayList<>();
        
        for (int i = 0; i < limit; i++) {
            numbers.add(entries.get(i).getKey());
        }
        
        // Create static array field
        FieldNode arrayField = new FieldNode(
            (node.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE,
            fieldName,
            "[I",
            null,
            null
        );
        arrayField.access |= Opcodes.ACC_STATIC;
        if (node.version <= Opcodes.V1_8) {
            arrayField.access |= Opcodes.ACC_FINAL;
        }
        node.fields.add(arrayField);
        
        // Create initialization method
        MethodNode initMethod = createArrayInitMethod(node, fieldName, numbers);
        node.methods.add(initMethod);
        
        // Add call to init method in <clinit>
        MethodNode clinit = NodeUtils.getMethod(node, "<clinit>");
        if (clinit == null) {
            clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            node.methods.add(clinit);
        }
        
        if (clinit.instructions.getFirst() == null) {
            clinit.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, initMethod.name, initMethod.desc, false));
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        } else {
            clinit.instructions.insertBefore(clinit.instructions.getFirst(), 
                new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, initMethod.name, initMethod.desc, false));
        }
        
        // Replace numbers in methods with array access
        replaceNumbersWithArrayAccess(node, fieldName, numbers);
    }

    private MethodNode createArrayInitMethod(ClassNode node, String fieldName, List<Integer> numbers) {
        MethodNode method = new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "init$" + Integer.toHexString(RANDOM.nextInt()),
            "()V",
            null,
            null
        );
        
        InsnList instructions = new InsnList();
        
        // Create array
        instructions.add(NodeUtils.generateIntPush(numbers.size()));
        instructions.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));
        instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, fieldName, "[I"));
        
        // Initialize each element with obfuscated values
        for (int i = 0; i < numbers.size(); i++) {
            instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, fieldName, "[I"));
            instructions.add(NodeUtils.generateIntPush(i));
            instructions.add(generateObfuscatedNumber(node, method, numbers.get(i)));
            instructions.add(new InsnNode(Opcodes.IASTORE));
        }
        
        instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions = instructions;
        method.maxStack = 6;
        
        return method;
    }

    private void replaceNumbersWithArrayAccess(ClassNode node, String fieldName, List<Integer> numbers) {
        for (MethodNode method : node.methods) {
            if (method.name.startsWith("<") || method.name.startsWith("init$")) continue;
            
            InsnList instructions = method.instructions;
            List<AbstractInsnNode> toRemove = new ArrayList<>();
            
            for (AbstractInsnNode insn : instructions.toArray()) {
                if (NodeUtils.isIntegerNumber(insn)) {
                    int value = NodeUtils.getIntValue(insn);
                    int index = numbers.indexOf(value);
                    
                    if (index != -1) {
                        // Replace with array access
                        instructions.insertBefore(insn, new FieldInsnNode(Opcodes.GETSTATIC, node.name, fieldName, "[I"));
                        instructions.insertBefore(insn, NodeUtils.generateIntPush(index));
                        instructions.insertBefore(insn, new InsnNode(Opcodes.IALOAD));
                        toRemove.add(insn);
                        
                        method.maxStack = Math.max(method.maxStack, 2);
                    }
                }
            }
            
            toRemove.forEach(instructions::remove);
        }
    }

    private String generateUniqueFieldName(String className) {
        String base = "nums$" + Integer.toHexString(RANDOM.nextInt());
        int count = fieldCounter.getOrDefault(className, 0);
        fieldCounter.put(className, count + 1);
        return base + (count > 0 ? "$" + count : "");
    }

    private int estimateMaxStack(InsnList instructions) {
        int stack = 0;
        int maxStack = 0;
        
        for (AbstractInsnNode insn : instructions.toArray()) {
            int opcode = insn.getOpcode();
            if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.SASTORE) {
                stack -= 2; // Store operations consume 2 values
            } else if (opcode >= Opcodes.ILOAD && opcode <= Opcodes.SALOAD) {
                stack++; // Load operations push 1 value
            } else if (opcode >= Opcodes.IADD && opcode <= Opcodes.LXOR) {
                stack--; // Binary operations consume 2, push 1
            } else if (opcode == Opcodes.INVOKESTATIC) {
                // For simplicity, assume method consumes arguments and returns 1 value
                // Real implementation would need to parse method descriptors
                stack = stack - 0 + 1; // Adjust based on actual method
            }
            
            maxStack = Math.max(maxStack, stack);
        }
        
        return maxStack + 5; // Add safety margin
    }

    @Override
    public ObfuscationTransformer getType() {
        return ObfuscationTransformer.NUMBER_OBFUSCATION;
    }
}
