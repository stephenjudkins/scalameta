package scala.meta

import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.scalameta.ast._
import org.scalameta.invariants._
import org.scalameta.annotations._
import org.scalameta.unreachable
import semantic._
import syntactic.show._
import syntactic.parsers._, SyntacticInfo._

@root trait Tree extends Product {
  type ThisType <: Tree
  def parent: Option[Tree]
  final override def toString: String = this.show[Raw]
}

@branch trait Term extends Arg with Stat
object Term {
  @branch trait Ref extends Term with meta.Ref
  @ast class This(qual: Option[Predef.String]) extends Term.Ref
  @ast class Super(thisp: Option[Predef.String], superp: Option[Predef.String]) extends Term.Ref
  @ast class Name(value: Predef.String @nonEmpty, @trivia isBackquoted: Boolean = false) extends meta.Name with Term.Ref with Pat with Member with Has.TermName {
    require(keywords.contains(value) ==> isBackquoted)
    def name: Name = this
    def mods: Seq[Mod] = Nil
  }
  @ast class Select(qual: Term, selector: Term.Name, @trivia isPostfix: Boolean = false) extends Term.Ref with Pat
  @ast class Interpolate(prefix: Name, parts: Seq[Lit.String] @nonEmpty, args: Seq[Term]) extends Term {
    require(parts.length == args.length + 1)
  }
  @ast class Apply(fun: Term, args: Seq[Arg]) extends Term
  @ast class ApplyType(fun: Term, targs: Seq[Type] @nonEmpty) extends Term
  @ast class ApplyInfix(lhs: Term, op: Name, targs: Seq[Type], args: Seq[Arg]) extends Term
  @ast class ApplyUnary(op: Name, arg: Term) extends Term {
    require(op.isUnaryOp)
  }
  @ast class Assign(lhs: Term.Ref, rhs: Term) extends Term
  @ast class Update(lhs: Apply, rhs: Term) extends Term
  @ast class Return(expr: Term = Lit.Unit()) extends Term
  @ast class Throw(expr: Term) extends Term
  @ast class Ascribe(expr: Term, tpe: Type) extends Term
  @ast class Annotate(expr: Term, annots: Seq[Mod.Annot] @nonEmpty) extends Term with Has.Mods {
    def mods: Seq[Mod] = annots
  }
  @ast class Tuple(elements: Seq[Term] @nonEmpty) extends Term {
    require(elements.length > 1)
  }
  @ast class Block(stats: Seq[Stat]) extends Term with Scope {
    require(stats.forall(_.isBlockStat))
  }
  @ast class If(cond: Term, thenp: Term, elsep: Term = Lit.Unit()) extends Term
  @ast class Match(scrut: Term, cases: Cases) extends Term
  @ast class Try(expr: Term, catchp: Option[Term], finallyp: Option[Term]) extends Term
  @ast class Function(params: Seq[Param], body: Term) extends Term with Scope.Params {
    require(params.collect{ case named: Param.Named => named }.forall(_.default.isEmpty))
    require(params.exists(_.mods.exists(_.isInstanceOf[Mod.Implicit])) ==> (params.length == 1))
  }
  @ast class Cases(cases: Seq[Aux.Case] @nonEmpty) extends Term {
    def isPartialFunction = !parent.map(_ match { case _: Match => false; case _: Try => false; case _ => true }).getOrElse(false)
  }
  @ast class While(expr: Term, body: Term) extends Term
  @ast class Do(body: Term, expr: Term) extends Term
  @ast class For(enums: Seq[Enum] @nonEmpty, body: Term) extends Term with Scope {
    require(enums.head.isInstanceOf[Enum.Generator])
  }
  @ast class ForYield(enums: Seq[Enum] @nonEmpty, body: Term) extends Term with Scope
  @ast class New(templ: Aux.Template) extends Term
  @ast class Placeholder() extends Term
  @ast class Eta(term: Term) extends Term
}

@branch trait Type extends Tree with Param.Type with Scope.Template
object Type {
  @branch trait Ref extends Type with meta.Ref
  @ast class Name(value: String @nonEmpty, @trivia isBackquoted: Boolean = false) extends meta.Name with Type.Ref {
    require(keywords.contains(value) ==> isBackquoted)
  }
  @ast class Select(qual: Term.Ref, selector: Type.Name) extends Type.Ref {
    require(qual.isPath || qual.isInstanceOf[Term.Super])
  }
  @ast class Project(qual: Type, selector: Type.Name) extends Type.Ref
  @ast class Singleton(ref: Term.Ref) extends Type.Ref {
    require(ref.isPath)
  }
  @ast class Apply(tpe: Type, args: Seq[Type] @nonEmpty) extends Type
  @ast class ApplyInfix(lhs: Type, op: Name, rhs: Type) extends Type
  @ast class Function(params: Seq[Param.Type], res: Type) extends Type
  @ast class Tuple(elements: Seq[Type] @nonEmpty) extends Type {
    require(elements.length > 1)
  }
  @ast class Compound(tpes: Seq[Type], refinement: Seq[Stat] = Nil) extends Type with Scope.Refine {
    require(tpes.length == 1 ==> hasRefinement)
    require(refinement.forall(_.isRefineStat))
  }
  @ast class Existential(tpe: Type, quants: Seq[Stat] @nonEmpty) extends Type with Scope.Existential {
    require(quants.forall(_.isExistentialStat))
  }
  @ast class Annotate(tpe: Type, annots: Seq[Mod.Annot] @nonEmpty) extends Type with Has.Mods {
    def mods: Seq[Mod] = annots
  }
  @ast class Placeholder(bounds: Aux.TypeBounds) extends Type
}

@branch trait Pat extends Tree
object Pat {
  @ast class Wildcard() extends Pat
  @ast class SeqWildcard() extends Pat
  @ast class Bind(lhs: Term.Name, rhs: Pat) extends Pat
  @ast class Alternative(lhs: Pat, rhs: Pat) extends Pat
  @ast class Tuple(elements: Seq[Pat] @nonEmpty) extends Pat
  @ast class Extract(ref: Term.Ref, targs: Seq[Type], elements: Seq[Pat]) extends Pat {
    require(ref.isStableId)
  }
  @ast class ExtractInfix(lhs: Pat, ref: Term.Name, rhs: Seq[Pat] @nonEmpty) extends Pat {
    require(ref.isStableId)
  }
  @ast class Interpolate(prefix: Term.Name, parts: Seq[Lit.String] @nonEmpty, args: Seq[Pat]) extends Pat {
    require(parts.length == args.length + 1)
  }
  @ast class Typed(lhs: Pat, rhs: Type) extends Pat {
    require(lhs.isInstanceOf[Pat.Wildcard] || lhs.isInstanceOf[Term.Name])
  }
}

@branch trait Lit extends Term with Pat with Type
object Lit {
  @ast class Bool(value: scala.Boolean) extends Lit
  @ast class Int(value: scala.Int) extends Lit
  @ast class Long(value: scala.Long) extends Lit
  @ast class Float(value: scala.Float) extends Lit
  @ast class Double(value: scala.Double) extends Lit
  @ast class Char(value: scala.Char) extends Lit
  @ast class String(value: Predef.String) extends Lit
  @ast class Symbol(value: scala.Symbol) extends Lit
  @ast class Null() extends Lit
  @ast class Unit() extends Lit
}

@branch trait Decl extends Stat with Has.Mods
object Decl {
  @ast class Val(mods: Seq[Mod],
                 pats: Seq[Term.Name] @nonEmpty,
                 decltpe: meta.Type) extends Decl
  @ast class Var(mods: Seq[Mod],
                 pats: Seq[Term.Name] @nonEmpty,
                 decltpe: meta.Type) extends Decl
  @ast class Def(mods: Seq[Mod],
                 name: Term.Name,
                 tparams: Seq[TypeParam],
                 explicits: Seq[Seq[Param.Named]],
                 implicits: Seq[Param.Named],
                 decltpe: meta.Type) extends Decl with Member.Def
  @ast class Procedure(mods: Seq[Mod],
                       name: Term.Name,
                       tparams: Seq[TypeParam],
                       explicits: Seq[Seq[Param.Named]],
                       implicits: Seq[Param.Named]) extends Decl with Member.Def
  @ast class Type(mods: Seq[Mod],
                  name: meta.Type.Name,
                  tparams: Seq[TypeParam],
                  bounds: Aux.TypeBounds) extends Decl with Member.Type
}

@branch trait Defn extends Stat with Has.Mods
object Defn {
  @ast class Val(mods: Seq[Mod],
                 pats: Seq[Pat] @nonEmpty,
                 decltpe: Option[meta.Type],
                 rhs: Term) extends Defn
  @ast class Var(mods: Seq[Mod],
                 pats: Seq[Pat] @nonEmpty,
                 decltpe: Option[meta.Type],
                 rhs: Option[Term]) extends Defn {
    require(rhs.isEmpty ==> pats.forall(_.isInstanceOf[Term.Name]))
    require(decltpe.nonEmpty || rhs.nonEmpty)
  }
  @ast class Def(mods: Seq[Mod],
                 name: Term.Name,
                 tparams: Seq[TypeParam],
                 explicits: Seq[Seq[Param.Named]],
                 implicits: Seq[Param.Named],
                 decltpe: Option[meta.Type],
                 body: Term) extends Defn with Member.Def
  @ast class Procedure(mods: Seq[Mod],
                       name: Term.Name,
                       tparams: Seq[TypeParam],
                       explicits: Seq[Seq[Param.Named]],
                       implicits: Seq[Param.Named],
                       stats: Seq[Stat]) extends Defn with Member.Def
  @ast class Macro(mods: Seq[Mod],
                   name: Term.Name,
                   tparams: Seq[TypeParam],
                   explicits: Seq[Seq[Param.Named]],
                   implicits: Seq[Param.Named],
                   tpe: meta.Type,
                   body: Term) extends Defn with Member.Term with Has.TermName
  @ast class Type(mods: Seq[Mod],
                  name: meta.Type.Name,
                  tparams: Seq[TypeParam],
                  body: meta.Type) extends Defn with Member.Type
  @ast class Class(mods: Seq[Mod],
                   name: meta.Type.Name,
                   override val tparams: Seq[TypeParam],
                   ctor: Ctor.Primary,
                   templ: Aux.Template) extends Defn with Member.Type with Has.Template with Has.TypeName
  @ast class Trait(mods: Seq[Mod],
                   name: meta.Type.Name,
                   override val tparams: Seq[TypeParam],
                   templ: Aux.Template) extends Defn with Member.Type with Has.Template with Has.TypeName {
    require(templ.stats.forall(!_.isInstanceOf[Ctor]))
    require(templ.parents.forall(_.argss.isEmpty))
  }
  @ast class Object(mods: Seq[Mod],
                    name: Term.Name,
                    templ: Aux.Template) extends Defn with Member.Term with Has.Template with Has.TermName {
  }
}

@ast class Pkg(ref: Term.Ref, stats: Seq[Stat], @trivia hasBraces: Boolean = true)
     extends Stat with Scope.TopLevel with Member.Term with Has.TermName {
  require(ref.isQualId)
  require(stats.forall(_.isTopLevelStat))
  def mods: Seq[Mod] = Nil
  def name: Term.Name = ref match {
    case name: Term.Name      => name
    case Term.Select(_, name) => name
    case _                    => unreachable
  }
}
object Pkg {
  @ast class Object(mods: Seq[Mod], name: Term.Name, templ: Aux.Template)
       extends Stat with Member.Term with Has.Template with Has.TermName
}

@branch trait Ctor extends Tree with Has.Mods with Has.Paramss
object Ctor {
  @ast class Primary(mods: Seq[Mod],
                     explicits: Seq[Seq[Param.Named]],
                     implicits: Seq[Param.Named]) extends Ctor
  @ast class Secondary(mods: Seq[Mod],
                       explicits: Seq[Seq[Param.Named]] @nonEmpty,
                       implicits: Seq[Param.Named],
                       primaryCtorArgss: Seq[Seq[Arg]],
                       stats: Seq[Stat]) extends Ctor with Stat with Scope.Params
}

@ast class Import(clauses: Seq[Import.Clause] @nonEmpty) extends Stat
object Import {
  @ast class Clause(ref: Term.Ref, sels: Seq[Selector] @nonEmpty) extends Tree {
    require(ref.isStableId)
  }

  @branch trait Selector extends Tree
  @ast class Wildcard() extends Selector
  @ast class Name(value: String, @trivia isBackquoted: Boolean = false) extends meta.Name with Selector
  @ast class Rename(from: String, to: String) extends Selector
  @ast class Unimport(name: String) extends Selector
}

@branch trait Param extends Tree with Has.Mods {
  def decltpe: Option[Param.Type]
}
object Param {
  @branch trait Type extends Tree
  object Type {
    @ast class ByName(tpe: Type) extends Type
    @ast class Repeated(tpe: Type) extends Type
  }
  @ast class Anonymous(mods: Seq[Mod],
                       decltpe: Option[Type]) extends Param
  @branch trait Named extends Param with Member.Term with Has.TermName{
    def mods: Seq[Mod]
    def name: Term.Name
    def decltpe: Option[Type]
    def default: Option[Term]
  }
  object Named {
    @ast class Simple(mods: Seq[Mod], name: Term.Name, decltpe: Option[Type], default: Option[Term]) extends Named
    @ast class Val(mods: Seq[Mod], name: Term.Name, decltpe: Option[Type], default: Option[Term]) extends Named
    @ast class Var(mods: Seq[Mod], name: Term.Name, decltpe: Option[Type], default: Option[Term]) extends Named
  }
}

@branch trait TypeParam extends Tree with Has.Mods with Has.TypeParams {
  def tparams: Seq[TypeParam]
  def contextBounds: Seq[meta.Type]
  def viewBounds: Seq[meta.Type]
  def bounds: Aux.TypeBounds
}
object TypeParam {
  @ast class Anonymous(mods: Seq[Mod],
                       tparams: Seq[TypeParam],
                       contextBounds: Seq[meta.Type],
                       viewBounds: Seq[meta.Type],
                       bounds: Aux.TypeBounds) extends TypeParam
  @ast class Named(mods: Seq[Mod],
                   name: meta.Type.Name,
                   tparams: Seq[TypeParam],
                   contextBounds: Seq[meta.Type],
                   viewBounds: Seq[meta.Type],
                   bounds: Aux.TypeBounds) extends TypeParam with Member.Type with Has.TypeName
}

@branch trait Arg extends Tree
object Arg {
  @ast class Named(name: Term.Name, rhs: Term) extends Arg
  @ast class Repeated(arg: Term) extends Arg
}

@branch trait Enum extends Tree
object Enum {
  @ast class Generator(pat: Pat, rhs: Term) extends Enum
  @ast class Val(pat: Pat, rhs: Term) extends Enum
  @ast class Guard(cond: Term) extends Enum
}

@branch trait Mod extends Tree
object Mod {
  @ast class Annot(tpe: Type, argss: Seq[Seq[Arg]]) extends Mod
  @ast class Doc(doc: String) extends Mod
  @ast class Private extends Mod
  @ast class PrivateThis extends Mod
  @ast class PrivateWithin(name: Predef.String) extends Mod
  @ast class Protected extends Mod
  @ast class ProtectedThis extends Mod
  @ast class ProtectedWithin(name: Predef.String) extends Mod
  @ast class Implicit() extends Mod
  @ast class Final() extends Mod
  @ast class Sealed() extends Mod
  @ast class Override() extends Mod
  @ast class Case() extends Mod
  @ast class Abstract() extends Mod
  @ast class Covariant() extends Mod
  @ast class Contravariant() extends Mod
  @ast class Lazy() extends Mod
}

object Aux {
  @ast class CompUnit(stats: Seq[Stat]) extends Tree {
    require(stats.forall(_.isTopLevelStat))
  }
  @ast class Case(pat: Pat, cond: Option[Term], stats: Seq[Stat]) extends Tree with Scope
  @ast class Parent(tpe: Type, argss: Seq[Seq[Arg]]) extends Tree
  @ast class Template(early: Seq[Stat],
                      parents: Seq[Parent],
                      self: Self,
                      stats: Seq[Stat] = Nil) extends Tree with Scope.Template {
    require(parents.isEmpty || !parents.tail.exists(_.argss.nonEmpty))
    require(early.nonEmpty ==> parents.nonEmpty)
    require(early.forall(_.isEarlyStat))
    require(stats.forall(_.isTemplateStat))
  }
  @ast class Self(name: Option[Term.Name], decltpe: Option[Type], @trivia hasThis: Boolean = false) extends Member.Term {
    def mods: Seq[Mod] = Nil
    require(hasThis ==> name.isEmpty)
  }
  @ast class TypeBounds(lo: Type = Type.Name("Nothing"), hi: Type = Type.Name("Any")) extends Tree
}

@branch trait Ref extends Tree

@branch trait Name extends Ref {
  def value: String
  def isBackquoted: Boolean
}

object Has {
  @branch trait Mods extends Tree {
    def mods: Seq[Mod]
  }
  @branch trait Paramss extends Tree with Scope.Params {
    def explicits: Seq[Seq[Param.Named]]
    def implicits: Seq[Param.Named]
    def paramss: Seq[Seq[Param.Named]] = explicits :+ implicits
  }
  @branch trait TypeParams extends Tree with Scope.Params {
    def tparams: Seq[TypeParam]
  }
  @branch trait Template extends Defn with Has.Name with Has.TypeParams with Has.Paramss with Scope.Template {
    def name: meta.Name
    def explicits: Seq[Seq[Param.Named]] = Nil
    def implicits: Seq[Param.Named] = Nil
    def tparams: Seq[TypeParam] = Nil
    def templ: Aux.Template
  }
  @branch trait Name extends Member { def name: meta.Name }
  @branch trait TermName extends Has.Name { def name: Term.Name }
  @branch trait TypeName extends Has.Name { def name: Type.Name }
}

@branch trait Member extends Tree with Stat with Has.Mods
object Member {
  @branch trait Term extends Member
  @branch trait Type extends Member with Has.TypeName with Has.TypeParams
  @branch trait Def extends Term with Has.TermName with Has.TypeParams with Has.Paramss
}

@branch trait Stat extends Tree

@branch trait Scope extends Tree
object Scope {
  @branch trait TopLevel extends Scope with Block
  @branch trait Template extends Block with Params
  @branch trait Block extends Refine
  @branch trait Refine extends Existential
  @branch trait Existential extends Scope
  @branch trait Params extends Scope
}