package scala.scalanative
package compiler
package pass

import scala.collection.mutable
import compiler.analysis.ClassHierarchy
import compiler.analysis.ClassHierarchyExtractors._
import util.ScopedVar, ScopedVar.scoped
import nir._

/** Lowers strings values into intrinsified global constants.
 *
 *  Eliminates:
 *  - Val.String
 */
class StringLowering(implicit chg: ClassHierarchy.Graph) extends Pass {
  import StringLowering._

  private val strings = mutable.UnrolledBuffer.empty[String]

  /** Names of the fields of the java.lang.String in the memory layout order. */
  private val stringFieldNames = {
    val node  = ClassRef.unapply(StringName).get
    val names = node.fields.sortBy(_.index).map(_.name)
    assert(names.length == 4, "java.lang.String is expected to have 4 fields.")
    names
  }

  override def preVal = {
    case Val.String(v) =>
      val node = ClassRef.unapply(StringName).get

      val stringInfo  = Val.Global(StringName tag "const", Type.Ptr)
      val charArrInfo = Val.Global(CharArrayName tag "const", Type.Ptr)
      val chars       = v.toCharArray
      val charsLength = Val.I32(chars.length)
      val charsConst = Val.Const(
          Val.Struct(
              Global.None,
              Seq(charArrInfo,
                  charsLength,
                  Val.Array(Type.I16, chars.map(c => Val.I16(c.toShort))))))

      val fieldValues = stringFieldNames.map {
        case StringValueName          => charsConst
        case StringOffsetName         => Val.I32(0)
        case StringCountName          => charsLength
        case StringCachedHashCodeName => Val.I32(v.hashCode)
        case _                        => util.unreachable
      }

      Val.Const(Val.Struct(Global.None, stringInfo +: fieldValues))
  }
}

object StringLowering extends PassCompanion {
  def apply(ctx: Ctx) = new StringLowering()(ctx.chg)

  val StringName               = Rt.String.name
  val StringValueName          = StringName member "value" tag "field"
  val StringOffsetName         = StringName member "offset" tag "field"
  val StringCountName          = StringName member "count" tag "field"
  val StringCachedHashCodeName = StringName member "cachedHashCode" tag "field"

  val CharArrayName = Global.Top("scala.scalanative.runtime.CharArray")

  override val depends = Seq(StringName,
                             StringValueName,
                             StringOffsetName,
                             StringCountName,
                             StringCachedHashCodeName,
                             CharArrayName)
}
