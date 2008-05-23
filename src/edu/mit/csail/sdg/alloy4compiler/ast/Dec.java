/*
 * Alloy Analyzer 4 -- Copyright (c) 2006-2008, Felix Chang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.mit.csail.sdg.alloy4compiler.ast;

import java.util.List;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.alloy4.ConstList;

/** Immutable; this declaration binds a list of variables to an expression. */

public final class Dec {

    /** If nonnull, then this decl is private (and this.isPrivate is the location of the "private" keyword) */
    public final Pos isPrivate;

    /** If nonnull, then each name is disjoint (and this.disjoint is the location of the "disjoint" keyword) */
    public final Pos disjoint;

    /** If nonnull, then each atom of this sig has disjoint value for this field (and this.disjoint2 is the location of the "disjoint" keyword) */
    public final Pos disjoint2;

    /** The list of names. */
    public final ConstList<ExprVar> names;

    /** The value that the list of names are bound to. */
    public final Expr expr;

    /** Caches the span() result. */
    private Pos span = null;

    /** Returns a Pos object representing the entire span of this expression and all its subexpressions. */
    public Pos span() {
        Pos p=span;
        if (p==null) {
            p=expr.span().merge(disjoint).merge(disjoint2);
            for(ExprVar n:names) p=p.merge(n.span());
            span=p;
        }
        return p;
    }

    /** This constructs a declaration. */
    public Dec(Pos isPrivate, Pos disjoint, Pos disjoint2, List<ExprVar> vars, Expr expr) {
        this.isPrivate = isPrivate;
        this.disjoint = disjoint;
        this.disjoint2 = disjoint2;
        this.names = ConstList.make(vars);
        this.expr = expr;
    }

    /** If the list of declaration contains a duplicate label, return one such duplicate label, else return null. */
    public static ExprVar findDuplicateLabel (List<Dec> list) {
        for(int i=0; i<list.size(); i++) {
            Dec d=list.get(i);
            for(int j=0; j<d.names.size(); j++) {
                ExprVar n=d.names.get(j);
                for(int k=j+1; k<d.names.size(); k++) if (d.names.get(k).label.equals(n.label)) return n;
                for(int k=i+1; k<list.size(); k++) if (list.get(k).hasName(n.label)) return n;
            }
        }
        return null;
    }

    /** Returns true if this declaration contains the given label. */
    public boolean hasName(String label) {
        for(int i=0; i<names.size(); i++) if (names.get(i).label.equals(label)) return true;
        return false;
    }
}