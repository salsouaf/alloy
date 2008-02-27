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

package edu.mit.csail.sdg.alloy4compiler.parser;

import java.util.List;
import edu.mit.csail.sdg.alloy4.ConstList;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorFatal;
import edu.mit.csail.sdg.alloy4.ErrorType;
import edu.mit.csail.sdg.alloy4.ErrorWarning;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.alloy4.Util;
import edu.mit.csail.sdg.alloy4compiler.ast.Expr;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprCustom;
import edu.mit.csail.sdg.alloy4compiler.parser.Module.Context;

/** Immutable; this class represents a macro. */

final class Macro extends ExprCustom {

    /** The module that defined this. */
    final Module realModule;

    /** If nonnull, this is a private macro. */
    final Pos isPrivate;

    /** The name of the macro. */
    final String name;

    /** The list of parameters (can be an empty list) */
    final ConstList<ExpName> params;

    /** The list of arguments (can be an empty list) (must be equal or shorter than this.params) */
    final ConstList<Expr> args;

    /** The macro body. */
    final Exp body;

    /** Construct a new Macro object. */
    Macro(Pos pos, Pos isPrivate, Module realModule, String name, List<ExpName> params, List<Expr> args, Exp body) {
        super(pos, new ErrorFatal(pos, "Incomplete call on the macro \""+name+"\""));
        this.realModule = realModule;
        this.isPrivate = isPrivate;
        this.name = name;
        this.params = ConstList.make(params);
        this.args = ConstList.make(args);
        this.body = body;
    }

    /** Construct a new Macro object. */
    Macro(Pos pos, Pos isPrivate, Module realModule, String name, List<ExpName> params, Exp body) {
        this(pos, isPrivate, realModule, name, params, null, body);
    }

    Macro addArg(Expr arg) {
        return new Macro(pos, isPrivate, realModule, name, params, Util.append(args,arg), body);
    }

    Expr changePos(Pos pos) {
        return new Macro(pos, isPrivate, realModule, name, params, args, body);
    }

    /** Returns the number of parameters still unfulfilled. */
    int gap() { return params.size() - args.size(); }

    /** Instantiate it. */
    Expr instantiate(Context cx, List<ErrorWarning> warnings) throws Err {
        if (cx.unrolls<=0) throw new ErrorType("Macro substitution too deep; possibly indicating an infinite recursion.");
        if (params.size() != args.size()) return this;
        Context cx2 = new Context(realModule, cx.unrolls-1);
        for(int n=params.size(), i=0; i<n; i++) {
            Expr tmp = args.get(i);
            if (!(tmp instanceof Macro)) tmp = tmp.resolve(tmp.type, warnings);
            cx2.put(params.get(i).name, tmp);
        }
        Expr ans = body.check(cx2, warnings);
        return ans;
    }

    /** {@inheritDoc} */
    @Override public String toString() { return name; }
}
