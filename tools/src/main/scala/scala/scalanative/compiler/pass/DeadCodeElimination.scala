package scala.scalanative
package compiler
package pass

import scala.collection.mutable
import analysis.ClassHierarchy
import nir._, Shows._
import util.sh

class DeadCodeElimination(implicit chg: ClassHierarchy.Graph) extends Pass {
  override def preDefn = {
    case defn: Defn.Define =>
      val usedef = analysis.UseDef(defn.blocks)

      val newBlocks = defn.blocks.map { block =>
        val newInsts = block.insts.filter {
          case Inst(n, op) => usedef(n).alive
        }

        block.copy(insts = newInsts)
      }

      Seq(defn.copy(blocks = newBlocks))
  }
}

object DeadCodeElimination extends PassCompanion {
  def apply(ctx: Ctx) = new DeadCodeElimination()(ctx.chg)
}
