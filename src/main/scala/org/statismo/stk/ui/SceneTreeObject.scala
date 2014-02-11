package org.statismo.stk.ui

import scala.swing.event.Event
import scala.collection.mutable.ArrayBuffer

object SceneTreeObject {
  case class ChildrenChanged(source: SceneTreeObject) extends Event
  case class VisibilityChanged(source: SceneTreeObject) extends Event
}

trait SceneTreeObject extends Nameable {
  lazy val parent: SceneTreeObject = ??? //you MUST override this. The exception that is thrown if you don't is intentional.
  def children: Seq[SceneTreeObject] = Nil

  def scene: Scene = {
    if (parent.isInstanceOf[Scene]) parent.asInstanceOf[Scene]
    else parent.scene
  }
  scene.listenTo(this)

  def displayables: List[Displayable] = {
    val cd = List(children.map(_.displayables).flatten).flatten
    if (this.isInstanceOf[Displayable]) {
      this.asInstanceOf[Displayable] :: cd
    } else cd
  }

  private var hidden: ArrayBuffer[Viewport] = new ArrayBuffer

  def isHiddenInViewport(viewport: Viewport) = hidden.exists({ v => v eq viewport })
  def isShownInViewport(viewport: Viewport) = !isHiddenInViewport(viewport)

  private def hideInViewports(viewports: Seq[Viewport], notify: Boolean): Unit = {
    val changed = viewports.map { viewport =>
      if (isShownInViewport(viewport)) {
        hidden += viewport
        true
      } else false
    }.foldLeft(false) { case (a, b) => a || b }
    children foreach { c => c.hideInViewports(viewports, notify && !changed) }
    if (changed && notify) {
      publish(SceneTreeObject.VisibilityChanged(this))
    }
  }

  private def showInViewports(viewports: Seq[Viewport], notify: Boolean): Unit = {
    val changed = viewports.map { viewport =>
      if (isHiddenInViewport(viewport)) {
        hidden -= viewport
        true
      } else false
    }.foldLeft(false) { case (a, b) => a || b }

    children foreach { c => c.showInViewports(viewports, notify && !changed) }
    if (changed && notify) {
      publish(SceneTreeObject.VisibilityChanged(this))
    }
  }

  def showInAllViewports: Unit = showInViewports(scene.viewports)
  def hideInAllViewports: Unit = hideInViewports(scene.viewports)
  
  def hideInViewports(viewports: Seq[Viewport]): Unit = hideInViewports(viewports, true)
  def showInViewports(viewports: Seq[Viewport]): Unit = showInViewports(viewports, true)

  def hideInViewport(viewport: Viewport): Unit = hideInViewports(Seq(viewport))
  def showInViewport(viewport: Viewport): Unit = showInViewports(Seq(viewport))
}
