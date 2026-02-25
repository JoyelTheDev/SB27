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

import me.superblaubeere27.jobf.processors.NumberObfuscationTransformer;
import me.superblaubeere27.jobf.utils.VariableProvider;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.*;

class SwitchMangler {
    
    private static final Random RANDOM = new Random();
    
    static void mangleSwitches(MethodNode node) {
        if (Modifier.isAbstract(node.access) || Modifier.isNative(node.access))
            return;

        VariableProvider provider = new VariableProvider(node);
        int resultSlot = provider.allocateVar();
        int tempSlot = provider.allocateVar(); // For additional obfuscation

        for (AbstractInsnNode abstractInsnNode : node.instructions.toArray()) {
            if (abstractInsnNode instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode switchInsnNode = (TableSwitchInsnNode) abstractInsnNode;
                InsnList insnList = obfuscateTableSwitch(switchInsnNode, resultSlot, tempSlot);
                node.instructions.insert(abstractInsnNode, insnList);
                node.instructions.remove(abstractInsnNode);
            }
            if (abstractInsnNode instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode switchInsnNode = (LookupSwitchInsnNode) abstractInsnNode;
                InsnList insnList = obfuscateLookupSwitch(switchInsnNode, resultSlot, tempSlot);
                node.instructions.insert(abstractInsnNode, insnList);
                node.instructions.remove(abstractInsnNode);
            }
        }
    }
    
    private static InsnList obfuscateTableSwitch(TableSwitchInsnNode switchInsnNode, 
                                                 int resultSlot, int tempSlot) {
        InsnList insnList = new InsnList();
        
        // Store the original switch value
        insnList.add(new VarInsnNode(Opcodes.ISTORE, resultSlot));
        
        // Apply transformation to the value before comparisons
        if (RANDOM.nextBoolean()) {
            // XOR transformation
            int xorKey = RANDOM.nextInt(10000) + 1;
            insnList.add(new VarInsnNode(Opcodes.ILOAD, resultSlot));
            insnList.add(NumberObfuscationTransformer.getInstructions(xorKey));
            insnList.add(new InsnNode(Opcodes.IXOR));
            insnList.add(new VarInsnNode(Opcodes.ISTORE, tempSlot));
            
            // Use transformed value for comparisons
            int j = 0;
            for (int i = switchInsnNode.min; i <= switchInsnNode.max; i++) {
                insnList.add(new VarInsnNode(Opcodes.ILOAD, tempSlot));
                // Transform the comparison value with same XOR key
                insnList.add(NumberObfuscationTransformer.getInstructions(i ^ xorKey));
                insnList.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, switchInsnNode.labels.get(j)));
                j++;
            }
        } else {
            // Use original approach but with scrambled order
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i <= switchInsnNode.max - switchInsnNode.min; i++) {
                indices.add(i);
            }
            Collections.shuffle(indices, RANDOM);
            
            // Create scrambled comparison order
            for (int idx : indices) {
                int value = switchInsnNode.min + idx;
                insnList.add(new VarInsnNode(Opcodes.ILOAD, resultSlot));
                insnList.add(NumberObfuscationTransformer.getInstructions(value));
                insnList.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, switchInsnNode.labels.get(idx)));
            }
        }
        
        insnList.add(new JumpInsnNode(Opcodes.GOTO, switchInsnNode.dflt));
        
        return insnList;
    }
    
    private static InsnList obfuscateLookupSwitch(LookupSwitchInsnNode switchInsnNode,
                                                  int resultSlot, int tempSlot) {
        InsnList insnList = new InsnList();
        
        // Store the original switch value
        insnList.add(new VarInsnNode(Opcodes.ISTORE, resultSlot));
        
        // Apply multiple obfuscation techniques
        int technique = RANDOM.nextInt(3);
        
        switch (technique) {
            case 0:
                // Basic scrambled order
                obfuscateLookupScrambled(insnList, switchInsnNode, resultSlot);
                break;
            case 1:
                // Hash-based transformation
                obfuscateLookupWithHash(insnList, switchInsnNode, resultSlot, tempSlot);
                break;
            case 2:
                // Binary search structure disguised as linear
                obfuscateLookupAsBinarySearch(insnList, switchInsnNode, resultSlot, tempSlot);
                break;
        }
        
        insnList.add(new JumpInsnNode(Opcodes.GOTO, switchInsnNode.dflt));
        
        return insnList;
    }
    
    private static void obfuscateLookupScrambled(InsnList insnList, LookupSwitchInsnNode switchInsnNode,
                                                 int resultSlot) {
        List<Integer> keys = switchInsnNode.keys;
        List<LabelNode> labels = switchInsnNode.labels;
        
        // Create shuffled order
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, RANDOM);
        
        for (int idx : indices) {
            insnList.add(new VarInsnNode(Opcodes.ILOAD, resultSlot));
            insnList.add(NumberObfuscationTransformer.getInstructions(keys.get(idx)));
            insnList.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, labels.get(idx)));
        }
    }
    
    private static void obfuscateLookupWithHash(InsnList insnList, LookupSwitchInsnNode switchInsnNode,
                                                int resultSlot, int tempSlot) {
        List<Integer> keys = switchInsnNode.keys;
        List<LabelNode> labels = switchInsnNode.labels;
        
        int hashMultiplier = RANDOM.nextInt(100) + 31;
        int hashAdd = RANDOM.nextInt(1000);
        
        // Transform the original value with hash-like operation
        insnList.add(new VarInsnNode(Opcodes.ILOAD, resultSlot));
        insnList.add(NumberObfuscationTransformer.getInstructions(hashMultiplier));
        insnList.add(new InsnNode(Opcodes.IMUL));
        insnList.add(NumberObfuscationTransformer.getInstructions(hashAdd));
        insnList.add(new InsnNode(Opcodes.IADD));
        insnList.add(new VarInsnNode(Opcodes.ISTORE, tempSlot));
        
        for (int i = 0; i < keys.size(); i++) {
            int transformedKey = keys.get(i) * hashMultiplier + hashAdd;
            insnList.add(new VarInsnNode(Opcodes.ILOAD, tempSlot));
            insnList.add(NumberObfuscationTransformer.getInstructions(transformedKey));
            insnList.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, labels.get(i)));
        }
    }
    
    private static void obfuscateLookupAsBinarySearch(InsnList insnList, LookupSwitchInsnNode switchInsnNode,
                                                      int resultSlot, int tempSlot) {
        List<Integer> keys = new ArrayList<>(switchInsnNode.keys);
        List<LabelNode> labels = new ArrayList<>(switchInsnNode.labels);
        
        // Sort keys for binary search structure
        List<Integer> sortedIndices = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            sortedIndices.add(i);
        }
        sortedIndices.sort(Comparator.comparingInt(keys::get));
        
        // Create a binary search tree structure disguised as linear comparisons
        createBinarySearchTree(insnList, switchInsnNode, sortedIndices, 0, sortedIndices.size() - 1, 
                               keys, labels, resultSlot);
    }
    
    private static void createBinarySearchTree(InsnList insnList, LookupSwitchInsnNode switchInsnNode,
                                               List<Integer> indices, int low, int high,
                                               List<Integer> keys, List<LabelNode> labels,
                                               int resultSlot) {
        if (low > high) return;
        
        int mid = (low + high) / 2;
        int idx = indices.get(mid);
        
        // Add comparison for mid value
        insnList.add(new VarInsnNode(Opcodes.ILOAD, resultSlot));
        insnList.add(NumberObfuscationTransformer.getInstructions(keys.get(idx)));
        
        // Create labels for less than and greater than paths
        LabelNode lessLabel = new LabelNode();
        LabelNode greaterLabel = new LabelNode();
        
        // Compare and branch
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPLT, lessLabel));
        insnList.add(new VarInsnNode(Opcodes.ILOAD, resultSlot));
        insnList.add(NumberObfuscationTransformer.getInstructions(keys.get(idx)));
        insnList.add(new JumpInsnNode(Opcodes.IF_ICMPGT, greaterLabel));
        
        // Equal case - go to actual label
        insnList.add(new JumpInsnNode(Opcodes.GOTO, labels.get(idx)));
        
        // Less than branch
        insnList.add(lessLabel);
        createBinarySearchTree(insnList, switchInsnNode, indices, low, mid - 1, keys, labels, resultSlot);
        insnList.add(new JumpInsnNode(Opcodes.GOTO, switchInsnNode.dflt));
        
        // Greater than branch
        insnList.add(greaterLabel);
        createBinarySearchTree(insnList, switchInsnNode, indices, mid + 1, high, keys, labels, resultSlot);
    }
}
