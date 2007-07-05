/*
 * Alloy Analyzer
 * Copyright (c) 2007 Massachusetts Institute of Technology
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA,
 * 02110-1301, USA
 */

package edu.mit.csail.sdg.alloy4compiler.ast;

import java.util.ArrayList;
import java.util.List;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorSyntax;
import edu.mit.csail.sdg.alloy4.ErrorType;
import edu.mit.csail.sdg.alloy4.IdentitySet;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig.PrimSig;

/**
 * Immutable; represents a formula or expression.
 *
 * <p>  <b>Invariant:</b>  pos!=null
 * <br> <b>Invariant:</b>  mult==0 || mult==1 || mult==2
 * <br> <b>Invariant:</b>  type!=null => (type.size()>0 || type.is_int || type.is_bool)
 *
 * <p>  If one or more subnode has null type, then the combined expr will also have null type
 * <br> If every subnode has valid bounding types, then the combined expr (if no exception) also has valid bounding type.
 * <br> If every subnode is fully resolved, then the combined expr (if no exception) also is fully resolved.
 */

public abstract class Expr {

    /** Accepts the return visitor. */
    abstract Object accept(VisitReturn visitor) throws Err;

    /**
     * Accepts the typecheck visitor for the first pass.
     *
     * <p>  Precondition:
     * <br> None
     *
     * <p>  Postcondition if no exception is thrown (where R is the return value):
     * <br> R.type.size()>0 || R.type.is_int || R.type.is_bool
     */
    abstract Expr check(TypeCheckContext ct) throws Err;

    /**
     * Accepts the typecheck visitor for the second pass.
     * (And if t.size()>0, it represents the set of tuples whose presence/absence is relevent to the parent expression)
     *
     * <p>  Precondition:
     * <br> None
     *
     * <p>  Postcondition if no exception is thrown (where R is the return value):
     * <br> R is fully resolved and has valid and unambiguous type
     */
    abstract Expr check(TypeCheckContext ct, Type t) throws Err;

    /** The filename, line, and column position in the original Alloy model file (cannot be null). */
    public final Pos pos;

    /** The type for this node; null if it is not well-typed. */
    public final Type type;

    /**
     * This field records whether the node is a multiplicity constraint.
     *
     * <br> If it's 2, that means it is an arrow multiplicity constraint (X ?->? X),
     *      or has the form (A -> B) where A and/or B is an arrow multiplicity constraint.
     *
     * <br> If it's 1, that means it is a multiplicity constraint of the form (? X)
     *
     * <br> If it's 0, that means it does not have either form.
     */
    public final int mult;

    /**
     * Each leaf Expr has a weight value (which can be 0 or higher);
     * by default, each nonleaf Expr's weight is the sum of its children.
     */
    public final long weight;

    /**
     * Constructs a new expression node
     *
     * @param pos - the original position in the file (null if unknown)
     *
     * @param type - the type (null if this expression has not been typechecked)
     *
     * @param mult - the multiplicity (which must be 0, 1, or 2)
     * <br>If it's 2, that means it is a multiplicity constraint (X ?->? X),
     *     or has the form (A -> B) where A and/or B is a multiplicity constraint.
     * <br>If it's 1, that means it is a multiplicity constraint of the form (? X)
     * <br>If it's 0, that means it does not have either form.
     *
     * @throws ErrorType if (type!=null && type.size()==0 && !type.is_int && !type.is_bool)
     */
    Expr (Pos pos, Type type, int mult, long weight) throws ErrorType {
        this.pos=(pos==null ? Pos.UNKNOWN : pos);
        this.mult=(mult<0 || mult>2) ? 0 : mult;
        this.type=type;
        this.weight=weight;
        if (type!=null && type.size()==0 && !type.is_int && !type.is_bool) throw new ErrorType(span(), "This expression failed to be typechecked");
    }

    /**
     * This must only be called by ExprConstant's constructor.
     * <p> precondition: type is unambiguous
     */
    Expr (Pos pos, Type type) {
        this.pos=(pos==null ? Pos.UNKNOWN : pos);
        this.mult=0;
        this.type=type;
        this.weight=0;
    }

    /**
     * This must only be called by Sig's constructor.
     * <p> if search!=null, we will look at "search", "search.parent()", "search.parent().parent()"... to try to find
     * the oldest parent whose "hint_isLeaf" flag is true (and if so, we'll use that node's type as the type)
     */
    Expr (Pos pos, Type type, PrimSig search) {
        while (search!=null) {
            if (search.hint_isLeaf) {
                type=search.type;
                break; // We can break early, because if an older node is also leaf, then this node would have inherited it too
            }
            search=search.parent;
        }
        this.pos=(pos==null ? Pos.UNKNOWN : pos);
        this.mult=0;
        this.type=(type==null ? Type.make((PrimSig)this) : type);
        this.weight=0;
    }

    /** Returns a Pos object representing the entire span of this Expr and all its subexpressions. */
    public abstract Pos span();

    /**
     * Print a textual description of it and all subnodes to a StringBuilder, with the given level of indentation.
     * (If indent<0, it will be printed in one line without line break)
     */
    public abstract void toString(StringBuilder out, int indent);

    /** Print a brief text description of it and all subnodes. */
    @Override public String toString() { StringBuilder sb=new StringBuilder(); toString(sb,-1); return sb.toString(); }

    /** A return visitor that determines whether the node (or a subnode) contains a predicate/function call. */
    private static final VisitQuery hasCall = new VisitQuery() {
        @Override public Object visit(ExprCall x) { return this; }
    };

    /** Returns true if the node (or a subnode) references a Func; can only be called on a typechecked node. */
    final boolean hasCall() throws Err { return accept(hasCall)!=null; }

    /** Transitively returns a set that contains all functions that this expression calls directly or indirectly. */
    public Iterable<Func> findAllFunctions() {
        final IdentitySet<Func> seen = new IdentitySet<Func>();
        final List<Func> todo = new ArrayList<Func>();
        final VisitQuery q = new VisitQuery() {
            @Override public final Object visit(ExprCall x) { if (seen.add(x.fun)) todo.add(x.fun); return null; }
        };
        try {
            q.visit(this);
            while(!todo.isEmpty()) { q.visit(todo.remove(todo.size()-1).getBody()); }
        } catch(Err ex) { } // Exception should not occur
        return seen;
    }

    //================================================================================//
    // Below are convenience methods for building up expressions from subexpressions. //
    //================================================================================//

    /**
     * Returns the formula (this and x)
     * <p> this and x must both be formulas
     * <p> Note: as a special guarantee, if x==null, then the method will return this Expr object as-is.
     */
    public final Expr and(Expr x) throws Err {
        if (x==null) return this;
        return ExprAnd.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the formula (this or x)
     * <p> this and x must both be formulas
     */
    public final Expr or(Expr x) throws Err {
        return ExprBinary.Op.OR.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the formula (this iff x)
     * <p> this and x must both be formulas
     */
    public final Expr iff(Expr x) throws Err {
        return ExprBinary.Op.IFF.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the formula (this implies x)
     * <p> this and x must both be formulas
     */
    public final Expr implies(Expr x) throws Err {
        return ExprBinary.Op.OR.make(span().merge(x.span()), not(), x);
    }

    /**
     * Returns the expression (this.x)
     * <p> 1. this must be a set or relation
     * <p> 2. x must be a set or relation
     * <p> 3. at most one of them can be a unary set
     */
    public final Expr join(Expr x) throws Err {
        return ExprBinary.Op.JOIN.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the expression (this <: x)
     * <p> this must be a unary set
     * <p> x must be a set or relation
     */
    public final Expr domain(Expr x) throws Err {
        return ExprBinary.Op.DOMAIN.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the expression (this :> x)
     * <p> this must be a set or relation
     * <p> x must be a unary set
     */
    public final Expr range(Expr x) throws Err {
        return ExprBinary.Op.RANGE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the expression (this intersects x)
     * <p> this and x must be expressions with the same arity
     */
    public final Expr intersect(Expr x) throws Err {
        return ExprBinary.Op.INTERSECT.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the expression (this++x)
     * <p> this and x must be expressions with the same arity
     */
    public final Expr override(Expr x) throws Err {
        return ExprBinary.Op.PLUSPLUS.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the expression (this+x)
     * <p> this and x must be expressions with the same arity, or both be integer expressions
     */
    public final Expr plus(Expr x) throws Err {
        return ExprBinary.Op.PLUS.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the expression (this-x)
     * <p> this and x must be expressions with the same arity, or both be integer expressions
     */
    public final Expr minus(Expr x) throws Err {
        return ExprBinary.Op.MINUS.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the formula (this==x)
     * <p> this and x must be expressions with the same arity, or both be integer expressions
     */
    public final Expr equal(Expr x) throws Err {
        return ExprBinary.Op.EQUALS.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the formula (this &lt; x)
     * <p> this and x must both be integer expressions
     */
    public final Expr lt(Expr x) throws Err {
        return ExprBinary.Op.LT.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the formula (this &lt;= x)
     * <p> this and x must both be integer expressions
     */
    public final Expr lte(Expr x) throws Err {
        return ExprBinary.Op.LTE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the formula (this &gt; x)
     * <p> this and x must both be integer expressions
     */
    public final Expr gt(Expr x) throws Err {
        return ExprBinary.Op.GT.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the formula (this &gt;= x)
     * <p> this and x must both be integer expressions
     */
    public final Expr gte(Expr x) throws Err {
        return ExprBinary.Op.GTE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the formula (this in x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation or multiplicity constraint
     * <p> this and x must have the same arity
     */
    public final Expr in(Expr x) throws Err {
        return ExprBinary.Op.IN.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the expression (this -> x) which is also a multiplicity constraint (this set->set x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr product(Expr x) throws Err {
        return ExprBinary.Op.ARROW.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this set->some x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr any_arrow_some(Expr x) throws Err {
        return ExprBinary.Op.ANY_ARROW_SOME.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this set->one x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr any_arrow_one(Expr x) throws Err {
        return ExprBinary.Op.ANY_ARROW_ONE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this set->lone x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr any_arrow_lone(Expr x) throws Err {
        return ExprBinary.Op.ANY_ARROW_LONE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this some->set x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr some_arrow_any(Expr x) throws Err {
        return ExprBinary.Op.SOME_ARROW_ANY.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this some->some x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr some_arrow_some(Expr x) throws Err {
        return ExprBinary.Op.SOME_ARROW_SOME.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this some->one x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr some_arrow_one(Expr x) throws Err {
        return ExprBinary.Op.SOME_ARROW_ONE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this some->lone x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr some_arrow_lone(Expr x) throws Err {
        return ExprBinary.Op.SOME_ARROW_LONE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this one->set x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr one_arrow_any(Expr x) throws Err {
        return ExprBinary.Op.ONE_ARROW_ANY.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this one->some x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr one_arrow_some(Expr x) throws Err {
        return ExprBinary.Op.ONE_ARROW_SOME.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this one->one x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr one_arrow_one(Expr x) throws Err {
        return ExprBinary.Op.ONE_ARROW_ONE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this one->lone x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr one_arrow_lone(Expr x) throws Err {
        return ExprBinary.Op.ONE_ARROW_LONE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this lone->set x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr lone_arrow_any(Expr x) throws Err {
        return ExprBinary.Op.LONE_ARROW_ANY.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this lone->some x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr lone_arrow_some(Expr x) throws Err {
        return ExprBinary.Op.LONE_ARROW_SOME.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this lone->one x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr lone_arrow_one(Expr x) throws Err {
        return ExprBinary.Op.LONE_ARROW_ONE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this lone->lone x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr lone_arrow_lone(Expr x) throws Err {
        return ExprBinary.Op.LONE_ARROW_LONE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the multiplicity constraint (this isSeq->lone x)
     * <p> this must be a set or relation
     * <p> x must be a set or relation
     */
    public final Expr isSeq_arrow_lone(Expr x) throws Err {
        return ExprBinary.Op.ISSEQ_ARROW_LONE.make(span().merge(x.span()), this, x);
    }

    /**
     * Returns the expression/integer/formula (this =&gt; x else y)
     * <p> this must be a formula
     * <p> x and y must both be expressions of the same arity, or both be integer expressions, or both be formulas
     */
    public final Expr ite(Expr x, Expr y) throws Err {
        return ExprITE.make(this, x, y);
    }

    /**
     * Returns the formula (all...| this)
     * <p> this must be a formula
     */
    public final Expr forAll(ExprVar... list) throws Err {
        if (list==null || list.length==0) throw new ErrorSyntax("You must have 1 or more variable in a quantification expression.");
        Pos p=Pos.UNKNOWN;
        for(ExprVar v:list) p=p.merge(v.span());
        return ExprQuant.Op.ALL.make(p, null, Util.asList(list), this);
    }

    /**
     * Returns the formula (no...| this)
     * <p> this must be a formula
     */
    public final Expr forNo(ExprVar... list) throws Err {
        if (list==null || list.length==0) throw new ErrorSyntax("You must have 1 or more variable in a quantification expression.");
        Pos p=Pos.UNKNOWN;
        for(ExprVar v:list) p=p.merge(v.span());
        return ExprQuant.Op.NO.make(p, null, Util.asList(list), this);
    }

    /**
     * Returns the formula (lone...| this)
     * <p> this must be a formula
     */
    public final Expr forLone(ExprVar... list) throws Err {
        if (list==null || list.length==0) throw new ErrorSyntax("You must have 1 or more variable in a quantification expression.");
        Pos p=Pos.UNKNOWN;
        for(ExprVar v:list) p=p.merge(v.span());
        return ExprQuant.Op.LONE.make(p, null, Util.asList(list), this);
    }

    /**
     * Returns the formula (one ...| this)
     * <p> this must be a formula
     */
    public final Expr forOne(ExprVar... list) throws Err {
        if (list==null || list.length==0) throw new ErrorSyntax("You must have 1 or more variable in a quantification expression.");
        Pos p=Pos.UNKNOWN;
        for(ExprVar v:list) p=p.merge(v.span());
        return ExprQuant.Op.ONE.make(p, null, Util.asList(list), this);
    }

    /**
     * Returns the formula (some...| this)
     * <p> this must be a formula
     */
    public final Expr forSome(ExprVar... list) throws Err {
        if (list==null || list.length==0) throw new ErrorSyntax("You must have 1 or more variable in a quantification expression.");
        Pos p=Pos.UNKNOWN;
        for(ExprVar v:list) p=p.merge(v.span());
        return ExprQuant.Op.SOME.make(p, null, Util.asList(list), this);
    }

    /**
     * Returns the comprehension expression {...|this}
     * <p> this must be a formula
     * <p> each declaration must be a "one-of" quantification over a unary set
     */
    public final Expr comprehensionOver(ExprVar... list) throws Err {
        if (list==null || list.length==0) throw new ErrorSyntax("You must have 1 or more variable in a quantification expression.");
        Pos p=Pos.UNKNOWN;
        for(ExprVar v:list) p=p.merge(v.span());
        return ExprQuant.Op.COMPREHENSION.make(p, null, Util.asList(list), this);
    }

    /**
     * Returns the integer (sum...| this)
     * <p> this must be an integer expression
     * <p> each declaration must be a "one-of" quantification over a unary set
     */
    public final Expr sumOver(ExprVar... list) throws Err {
        if (list==null || list.length==0) throw new ErrorSyntax("You must have 1 or more variable in a quantification expression.");
        Pos p=Pos.UNKNOWN;
        for(ExprVar v:list) p=p.merge(v.span());
        return ExprQuant.Op.SUM.make(p, null, Util.asList(list), this);
    }

    /**
     * Return a quantified variable (label: some this)
     * <p> this must be already fully typechecked, and must be a unary set
     * <p> the label is only used for pretty-printing, and does not need to be unique
     */
    public final ExprVar someOf(String label) throws Err {
        Expr x = TypeCheckContext.cset(this);
        x = ExprUnary.Op.SOMEOF.make(span(), x);
        return new ExprVar(span(), label, x);
    }

    /**
     * Return a quantified variable (label: lone this)
     * <p> this must be already fully typechecked, and must be a unary set
     * <p> the label is only used for pretty-printing, and does not need to be unique
     */
    public final ExprVar loneOf(String label) throws Err {
        Expr x = TypeCheckContext.cset(this);
        x = ExprUnary.Op.LONEOF.make(span(), x);
        return new ExprVar(span(), label, x);
    }

    /**
     * Return a quantified variable (label: one this)
     * <p> this must be already fully typechecked, and must be a unary set
     * <p> the label is only used for pretty-printing, and does not need to be unique
     */
    public final ExprVar oneOf(String label) throws Err {
        Expr x = TypeCheckContext.cset(this);
        x = ExprUnary.Op.ONEOF.make(span(), x);
        return new ExprVar(span(), label, x);
    }

    /**
     * Return a quantified variable (label: set this)
     * <p> this must be already fully typechecked, and must be a set or relation
     * <p> the label is only used for pretty-printing, and does not need to be unique
     */
    public final ExprVar setOf(String label) throws Err {
        Expr x = TypeCheckContext.cset(this);
        x = ExprUnary.Op.SETOF.make(span(), x);
        return new ExprVar(span(), label, x);
    }

    /**
     * Returns the formula (not this)
     * <p> this must be a formula
     */
    public final Expr not() throws Err {
        return ExprUnary.Op.NOT.make(span(), this);
    }

    /**
     * Returns the formula (no this)
     * <p> this must be a set or a relation
     */
    public final Expr no() throws Err {
        return ExprUnary.Op.NO.make(span(), this);
    }

    /**
     * Returns the formula (some this)
     * <p> this must be a set or a relation
     */
    public final Expr some() throws Err {
        return ExprUnary.Op.SOME.make(span(), this);
    }

    /**
     * Returns the formula (lone this)
     * <p> this must be a set or a relation
     */
    public final Expr lone() throws Err {
        return ExprUnary.Op.LONE.make(span(), this);
    }

    /**
     * Returns the formula (one this)
     * <p> this must be a set or a relation
     */
    public final Expr one() throws Err {
        return ExprUnary.Op.ONE.make(span(), this);
    }

    /**
     * Returns the expression (~this)
     * <p> this must be a binary relation
     */
    public final Expr transpose() throws Err {
        return ExprUnary.Op.TRANSPOSE.make(span(), this);
    }

    /**
     * Returns the expression (*this)
     * <p> this must be a binary relation
     */
    public final Expr reflexiveClosure() throws Err {
        return ExprUnary.Op.RCLOSURE.make(span(), this);
    }

    /**
     * Returns the expression (^this)
     * <p> this must be a binary relation
     */
    public final Expr closure() throws Err {
        return ExprUnary.Op.CLOSURE.make(span(), this);
    }

    /**
     * Returns the integer expression (#this) truncated to the current integer bitwidth.
     * <p> this must be a set or a relation
     */
    public final Expr cardinality() throws Err {
        return ExprUnary.Op.CARDINALITY.make(span(), this);
    }

    /**
     * Returns the integer int[this]
     * <p> this must be a unary set
     */
    public final Expr cast2int() throws Err {
        return ExprUnary.Op.CAST2INT.make(span(), this);
    }

    /**
     * Returns the expression Int[this]
     * <p> this must be an integer expression
     */
    public final Expr cast2sigint() throws Err {
        return ExprUnary.Op.CAST2SIGINT.make(span(), this);
    }
}