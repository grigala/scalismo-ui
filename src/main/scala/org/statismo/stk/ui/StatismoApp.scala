package org.statismo.stk.ui

import java.awt.Dimension

import scala.Array.canBuildFrom
import scala.swing.MainFrame
import scala.swing.MenuBar
import scala.swing.Reactor
import scala.swing.SimpleSwingApplication

import org.statismo.stk.ui.swing.menu.MainMenuBar
import org.statismo.stk.ui.view.swing.WorkspacePanel

import javax.swing.UIManager
import javax.swing.WindowConstants

object StatismoApp {
  type FrameConstructor = (Scene => StatismoFrame)
  def defaultFrameConstructor: FrameConstructor = { s: Scene => new StatismoFrame(s) }

  def apply(args: Array[String] = new Array[String](0), scene: Scene = new Scene, frame: FrameConstructor = defaultFrameConstructor, lookAndFeel: String = defaultLookAndFeelClassName): StatismoApp = {
    UIManager.setLookAndFeel(lookAndFeel)
    val app = new StatismoApp(frame(scene))
    app.main(args)
    app
  }

  def defaultLookAndFeelClassName: String = {
    val nimbus = UIManager.getInstalledLookAndFeels().filter(_.getName().equalsIgnoreCase("nimbus")).map(i => i.getClassName())
    if (!nimbus.isEmpty) nimbus.head else UIManager.getSystemLookAndFeelClassName()
  }
  
  val Version: String = "0.1.1"
}

class StatismoApp(val top: StatismoFrame) extends SimpleSwingApplication {
  override def startup(args: Array[String]) = {
    top.startup(args)
    super.startup(args)
  }

}

class StatismoFrame(val scene: Scene) extends MainFrame with Reactor {

  title = "Statismo Viewer"

  override def menuBar: MainMenuBar = {
    super.menuBar.asInstanceOf[MainMenuBar]
  }

  override def menuBar_=(mb: MenuBar) = {
    if (mb.isInstanceOf[MainMenuBar]) {
      super.menuBar_=(mb)
    } else {
      throw new RuntimeException("MenuBar must be of type org.statismo.stk.ui.swing.menu.MainMenuBar")
    }
  }

  val workspace = new Workspace(scene)
  contents = new WorkspacePanel(workspace)
  menuBar = new MainMenuBar()(this)

  peer.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE)
  override def closeOperation = {
    dispose
  }

  def startup(args: Array[String]): Unit = {
    size = new Dimension(800, 600)
    // center on screen
    peer.setLocationRelativeTo(null)
  }
}

