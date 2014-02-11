package org.statismo.stk.ui.vtk

import org.statismo.stk.ui.Viewport
import vtk.vtkRenderer
import scala.swing.Publisher
import scala.swing.event.Event
import org.statismo.stk.ui.Scene
import scala.collection.immutable.HashMap
import org.statismo.stk.ui.Displayable
import org.statismo.stk.ui.EdtPublisher

trait VtkContext extends EdtPublisher {

}
object VtkContext {
  case class RenderRequest(source: VtkContext) extends Event
  case class ViewportEmpty(source: VtkViewport) extends Event
}

class VtkViewport(val viewport: Viewport, val renderer: vtkRenderer, implicit val interactor: VtkRenderWindowInteractor) extends VtkContext {
  val scene = viewport.scene
  private var actors = new HashMap[Displayable, Option[DisplayableActor]]

  def refresh() {
    val backend = scene.displayables.filter(_.isShownInViewport(viewport))
    var changed = false

    // remove obsolete actors
    actors.filterNot({ case (back, front) => backend.exists({ _ eq back }) }).foreach({
      case (back, front) => {
        actors -= back
        if (front.isDefined) {
          deafTo(front.get)
          front.get.vtkActors.foreach({ a =>
            renderer.RemoveActor(a)
            changed = true
          })
        }
      }
    })

    // determine new actors
    val toCreate = backend.filterNot { d =>
      actors.keys.exists { k => k eq d }
    }

    val created = toCreate.map(d => Tuple2(d, DisplayableActor(d)))
    created.foreach({
      case (back, front) =>
        actors += Tuple2(back, front)
        if (front.isDefined) {
          listenTo(front.get)
          changed = true
          front.get.vtkActors.foreach({ a =>
            renderer.AddActor(a)
          })
        }
    })
    if (changed) {
      // FIXME
      renderer.ResetCamera()
      if (actors.isEmpty) {
        publish(VtkContext.ViewportEmpty(this))
      } else {
        publish(VtkContext.RenderRequest(this))
      }
    }
  }

  listenTo(scene)
  deafTo(this)
  reactions += {
    case Scene.TreeTopologyChanged(s) => refresh()
    case Scene.VisibilityChanged(s) => refresh()
    case VtkContext.RenderRequest(s) => publish(VtkContext.RenderRequest(this))
  }
  refresh()
}