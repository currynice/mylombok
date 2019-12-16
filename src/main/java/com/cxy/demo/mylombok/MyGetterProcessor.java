package com.cxy.demo.mylombok;

import com.cxy.demo.mylombok.annotations.Getter;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;


import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * SupportedAnnotationTypes processor需要的注解
 *
 */
@SupportedAnnotationTypes("com.cxy.demo.mylombok.annotations.Getter")
@SupportedSourceVersion(value = SourceVersion.RELEASE_8)
public class MyGetterProcessor extends AbstractProcessor {

    //是否已经初始化
    private boolean hasInited;

    //编译期间用来log错误、警报和通知
    private Messager messager;

    //语法数字
    private JavacTrees javacTrees;

    //创建数节点
    private TreeMaker treeMaker;

    //创建标识符号
    private Names names;

    //打印{编译期}的一些环境变量
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        if(hasInited){
            throw new IllegalStateException("AddPrintProcessor已经初始化过了");
        }
        this.messager = processingEnv.getMessager();
        this.javacTrees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment)processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);


        this.hasInited = true;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //获得所有标记 Getter 注解的elements
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Getter.class);


        if(elements.isEmpty()){
            System.out.println("No elements to process");
            return false;
        }

        //遍历Set
        elements.forEach(
                element->{
                    if (element instanceof TypeElement){
                        //生成jcTree语法树
                        JCTree jcTree = javacTrees.getTree(element);
                        //重写visitClassDef() ,处理语法数，得到type定义的jcClassDecl
                        jcTree.accept(new TreeTranslator(){
                            @Override
                            public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                                //遍历jcTree所有成员(成员变量，成员函数，构造函数)，保存其中的成员变量VARIABLE到jcVariableDeclsLists
                                List<JCTree.JCVariableDecl> jcVariableDeclsLists = List.nil();
                                for(JCTree tree : jcClassDecl.defs){
                                    if(tree.getKind().equals(Tree.Kind.VARIABLE)){
                                        JCTree.JCVariableDecl jcVariableDecls = (JCTree.JCVariableDecl)tree;
                                        jcVariableDeclsLists.append(jcVariableDecls);
                                    }
                                }

                                //为这个Type的所有成员变量加入一个 新的getXX  method
                                jcVariableDeclsLists.forEach(jcVariable->{
                                        //不支持链式调用，只能赋值回来
                                        jcClassDecl.defs = jcClassDecl.defs.prepend(makeGetterMethodDecl(jcVariable));
                                        messager.printMessage(Diagnostic.Kind.NOTE,jcVariable.getName()+"has been processed");
                                        });

                                //执行默认的visitClassDef
                                super.visitClassDef(jcClassDecl);
                            }
                        });
                    }
                });
        return true;
    }

    //private method
    private JCTree.JCMethodDecl makeGetterMethodDecl(JCTree.JCVariableDecl jcVariableDecl){
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        statements.append(treeMaker.Return(treeMaker.Select(treeMaker.Ident(names.fromString("this")),jcVariableDecl.getName())));
        JCTree.JCBlock body = treeMaker.Block(0,statements.toList());
        return treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC),getNewMethodName(jcVariableDecl.getName()),jcVariableDecl.vartype,List.nil(),List.nil(),List.nil(),body,null);
    }


    //驼峰
    private Name getNewMethodName(Name name){
        String s = name.toString();
        return names.fromString("get"+s.substring(0,1).toUpperCase()+s.substring(1,name.length()));
    }


}
