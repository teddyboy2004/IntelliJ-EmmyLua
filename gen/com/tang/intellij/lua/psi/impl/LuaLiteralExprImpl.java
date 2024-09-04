// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.tang.intellij.lua.psi.LuaLiteralExpr;
import com.tang.intellij.lua.psi.LuaVisitor;
import com.tang.intellij.lua.stubs.LuaLiteralExprStub;
import org.jetbrains.annotations.NotNull;

public class LuaLiteralExprImpl extends LuaLiteralExprMixin implements LuaLiteralExpr {

  public LuaLiteralExprImpl(@NotNull LuaLiteralExprStub stub, @NotNull IStubElementType<?, ?> nodeType) {
    super(stub, nodeType);
  }

  public LuaLiteralExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public LuaLiteralExprImpl(@NotNull LuaLiteralExprStub stub, @NotNull IElementType type, @NotNull ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull LuaVisitor visitor) {
    visitor.visitLiteralExpr(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof LuaVisitor) accept((LuaVisitor)visitor);
    else super.accept(visitor);
  }

}
