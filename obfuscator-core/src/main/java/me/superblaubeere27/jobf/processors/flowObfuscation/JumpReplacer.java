/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package me.superblaubeere27.jobf.processors.flowObfuscation;

import me.superblaubeere27.jobf.utils.NodeUtils;
import me.superblaubeere27.jobf.utils.Utils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class JumpReplacer {
    
    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
    private static final int MAX_OBFUSCATION_DEPTH = 3;
    
    public static void process(ClassNode node, MethodNode methodNode) {
        if (methodNode.instructions.size() < 5) return; // Skip small methods
        
        // Apply multiple obfuscation layers
        for (int layer = 0; layer < RANDOM.nextInt(MAX_OBFUSCATION_DEPTH) + 1; layer++) {
            switch (RANDOM.nextInt(5)) {
                case 0:
                    replaceGotoJumps(node, methodNode);
                    break;
                case 1:
                    introduceBogusConditionals(node, methodNode);
                    break;
                case 2:
                    splitBasicBlocks(node, methodNode);
                    break;
                case 3:
                    createOpaquePredicates(node, methodNode);
                    break;
                case 4:
                    addExceptionBasedJumps(node, methodNode);
                    break;
            }
        }
    }
    
    private static void replaceGotoJumps(ClassNode node, MethodNode methodNode) {
        List<LabelNode> gotoLabels = new ArrayList<>();
        List<JumpInsnNode> gotoJumps = new ArrayList<>();
        
        // Collect all GOTO jumps
        for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
            if (insn instanceof JumpInsnNode && insn.getOpcode() == Opcodes.GOTO) {
                JumpInsnNode jump = (JumpInsnNode) insn;
                gotoLabels.add(jump.label);
                gotoJumps.add(jump);
            }
        }
        
        if (gotoLabels.size() < 2) return;
        
        // Shuffle to create random pairs
        Collections.shuffle(gotoLabels);
        
        // Create replacement pairs
        Map<LabelNode, ReplacedLabelPair> replacementMap = new HashMap<>();
        List<ReplacedLabelPair> pairs = new ArrayList<>();
        
        for (int i = 0; i < gotoLabels.size() - 1; i += 2) {
            LabelNode first = gotoLabels.get(i);
            LabelNode second = gotoLabels.get(i + 1);
            ReplacedLabelPair pair = new ReplacedLabelPair(first, second);
            pairs.add(pair);
            replacementMap.put(first, pair);
            replacementMap.put(second, pair);
        }
        
        // Replace GOTO jumps with conditional logic
        for (JumpInsnNode gotoJump : gotoJumps) {
            ReplacedLabelPair pair = replacementMap.get(gotoJump.label);
            if (pair == null) continue;
            
            InsnList replacement = new InsnList();
            
            // Generate a random key value for this jump
            int key = RANDOM.nextInt(1000) - 500;
            
            // Create switch-like structure
            LabelNode actualTarget = (gotoJump.label == pair.first) ? pair.first : pair.second;
            LabelNode otherTarget = (gotoJump.label == pair.first) ? pair.second : pair.first;
            
            // Push a value that determines the jump target
            replacement.add(NodeUtils.generateIntPush(key));
            
            // Compare with pair's first number
            replacement.add(NodeUtils.generateIntPush(pair.firstNumber));
            replacement.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, actualTarget));
            
            // Compare with pair's second number
            replacement.add(NodeUtils.generateIntPush(pair.secondNumber));
            replacement.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, otherTarget));
            
            // Fallback - should never be reached, but adds confusion
            replacement.add(new JumpInsnNode(Opcodes.GOTO, pair.replacement));
            
            methodNode.instructions.insertBefore(gotoJump, replacement);
            methodNode.instructions.remove(gotoJump);
        }
        
        // Add the replacement switch table at the end
        InsnList switchTable = createSwitchTable(methodNode, pairs);
        if (switchTable != null) {
            methodNode.instructions.add(switchTable);
        }
    }
    
    private static void introduceBogusConditionals(ClassNode node, MethodNode methodNode) {
        InsnList instructions = methodNode.instructions;
        List<AbstractInsnNode> positions = new ArrayList<>();
        
        // Find positions to insert bogus conditionals
        for (int i = 0; i < instructions.size(); i += RANDOM.nextInt(5) + 3) {
            if (i < instructions.size()) {
                positions.add(instructions.get(i));
            }
        }
        
        for (AbstractInsnNode position : positions) {
            InsnList bogus = new InsnList();
            LabelNode alwaysTrue = new LabelNode();
            LabelNode neverReached = new LabelNode();
            
            // Create an always-true condition
            int x = RANDOM.nextInt(100);
            int y = x; // Always equal
            
            bogus.add(NodeUtils.generateIntPush(x));
            bogus.add(NodeUtils.generateIntPush(y));
            bogus.add(new JumpInsnNode(Opcodes.IF_ICMPNE, neverReached));
            bogus.add(new JumpInsnNode(Opcodes.GOTO, alwaysTrue));
            bogus.add(neverReached);
            bogus.add(new InsnNode(Opcodes.ATHROW)); // This will never execute
            bogus.add(alwaysTrue);
            
            instructions.insertBefore(position, bogus);
        }
    }
    
    private static void splitBasicBlocks(ClassNode node, MethodNode methodNode) {
        InsnList instructions = methodNode.instructions;
        List<AbstractInsnNode> splitPoints = new ArrayList<>();
        
        // Find split points (after certain instructions)
        for (AbstractInsnNode insn : instructions.toArray()) {
            if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) continue;
            if (insn instanceof JumpInsnNode) continue;
            
            if (RANDOM.nextInt(10) == 0) {
                splitPoints.add(insn);
            }
        }
        
        for (AbstractInsnNode splitPoint : splitPoints) {
            LabelNode newBlock = new LabelNode();
            
            // Insert a jump to the new block
            instructions.insertBefore(splitPoint, new JumpInsnNode(Opcodes.GOTO, newBlock));
            instructions.insert(splitPoint, newBlock);
        }
    }
    
    private static void createOpaquePredicates(ClassNode node, MethodNode methodNode) {
        InsnList instructions = methodNode.instructions;
        List<JumpInsnNode> conditionalJumps = new ArrayList<>();
        
        // Find all conditional jumps
        for (AbstractInsnNode insn : instructions.toArray()) {
            if (insn instanceof JumpInsnNode && insn.getOpcode() != Opcodes.GOTO 
                    && insn.getOpcode() != Opcodes.JSR) {
                conditionalJumps.add((JumpInsnNode) insn);
            }
        }
        
        for (JumpInsnNode jump : conditionalJumps) {
            if (RANDOM.nextInt(3) == 0) {
                // Replace with opaque predicate
                InsnList predicate = new InsnList();
                LabelNode actualTarget = jump.label;
                LabelNode fakeTarget = new LabelNode();
                
                // Create an opaque predicate that's always true
                int a = RANDOM.nextInt(1000);
                int b = a * 2;
                int c = b / 2;
                
                predicate.add(NodeUtils.generateIntPush(a));
                predicate.add(NodeUtils.generateIntPush(b));
                predicate.add(new InsnNode(Opcodes.IMUL));
                predicate.add(NodeUtils.generateIntPush(c));
                predicate.add(new InsnNode(Opcodes.IDIV));
                predicate.add(NodeUtils.generateIntPush(a));
                
                // This condition is always true: (a*b)/c == a
                predicate.add(new JumpInsnNode(jump.getOpcode(), fakeTarget));
                predicate.add(new JumpInsnNode(Opcodes.GOTO, actualTarget));
                predicate.add(fakeTarget);
                
                // Add dead code in fake target
                predicate.add(new InsnNode(Opcodes.POP));
                predicate.add(new InsnNode(Opcodes.POP));
                predicate.add(new InsnNode(Opcodes.ATHROW));
                
                instructions.insertBefore(jump, predicate);
                instructions.remove(jump);
            }
        }
    }
    
    private static void addExceptionBasedJumps(ClassNode node, MethodNode methodNode) {
        InsnList instructions = methodNode.instructions;
        
        // Create exception handler for flow obfuscation
        LabelNode tryStart = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode afterHandler = new LabelNode();
        
        // Insert try block markers
        instructions.insert(tryStart);
        
        // Find a position to split
        int splitPos = instructions.size() / 2;
        if (splitPos < instructions.size()) {
            AbstractInsnNode splitInsn = instructions.get(splitPos);
            instructions.insertBefore(splitInsn, tryEnd);
            instructions.insertBefore(splitInsn, new JumpInsnNode(Opcodes.GOTO, afterHandler));
            
            // Exception handler
            instructions.insert(splitInsn, handler);
            instructions.insert(handler, new InsnNode(Opcodes.POP)); // Remove exception
            
            // Restore flow
            instructions.insert(handler, new JumpInsnNode(Opcodes.GOTO, afterHandler));
            
            // Continue point
            instructions.insert(splitInsn, afterHandler);
            
            // Add try-catch block
            methodNode.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, handler, null));
        }
    }
    
    private static InsnList createSwitchTable(MethodNode methodNode, List<ReplacedLabelPair> pairs) {
        if (pairs.isEmpty()) return null;
        
        InsnList table = new InsnList();
        Type returnType = Type.getReturnType(methodNode.desc);
        
        LabelNode defaultCase = new LabelNode();
        
        // Create switch variable
        table.add(new VarInsnNode(Opcodes.ILOAD, methodNode.maxLocals));
        
        // Create lookup switch
        int[] keys = new int[pairs.size() * 2];
        LabelNode[] labels = new LabelNode[pairs.size() * 2];
        
        int index = 0;
        for (ReplacedLabelPair pair : pairs) {
            keys[index] = pair.firstNumber;
            labels[index] = pair.first;
            index++;
            
            keys[index] = pair.secondNumber;
            labels[index] = pair.second;
            index++;
        }
        
        table.add(new LookupSwitchInsnNode(defaultCase, keys, labels));
        table.add(defaultCase);
        
        // Default case - return if needed
        if (returnType.getSize() != 0) {
            table.add(NodeUtils.nullValueForType(returnType));
        }
        table.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
        
        return table;
    }
    
    /**
     * Advanced control flow flattening
     */
    public static void flattenControlFlow(ClassNode node, MethodNode methodNode) {
        // This is a more advanced technique that reorganizes the entire method
        // into a switch-based dispatcher
        
        // Collect all basic blocks
        List<BasicBlock> blocks = new ArrayList<>();
        Map<LabelNode, Integer> blockIndices = new HashMap<>();
        
        // Implementation of control flow flattening would go here
        // This is a complex transformation that would:
        // 1. Split method into basic blocks
        // 2. Assign each block an index
        // 3. Replace all jumps with updates to a state variable
        // 4. Add a main dispatch loop
    }
    
    static class ReplacedLabelPair {
        private int firstNumber;
        private int secondNumber;
        private LabelNode first;
        private LabelNode second;
        private LabelNode replacement;
        
        ReplacedLabelPair(LabelNode first, LabelNode second) {
            this.first = first;
            this.second = second;
            this.firstNumber = RANDOM.nextInt(1000) - 500;
            
            do {
                this.secondNumber = RANDOM.nextInt(1000) - 500;
            } while (this.secondNumber == this.firstNumber);
            
            this.replacement = new LabelNode();
        }
    }
    
    static class BasicBlock {
        LabelNode start;
        LabelNode end;
        List<AbstractInsnNode> instructions = new ArrayList<>();
        List<Integer> successors = new ArrayList<>();
    }
}
