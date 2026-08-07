package net.typho.big_shot.installer

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import javax.swing.*

object BigShotInstaller {
    data class Instance(
        @JvmField
        val id: String,
        @JvmField
        val name: String
    )

    fun getInstances(conn: Connection): List<Instance> {
        val result = mutableListOf<Instance>()

        conn.prepareStatement(
            "SELECT id, path FROM instances"
        ).use { st ->
            val rs = st.executeQuery()

            while (rs.next()) {
                result.add(
                    Instance(
                        rs.getString("id"),
                        rs.getString("path")
                    )
                )
            }
        }

        return result
    }

    fun getOverridesJson(conn: Connection, instanceId: String): String? {
        conn.prepareStatement(
            "SELECT json(overrides) FROM instance_launch_overrides WHERE instance_id = ?"
        ).use { st ->
            st.setString(1, instanceId)

            val rs = st.executeQuery()

            if (rs.next()) {
                return rs.getString(1)
            }
        }

        return null
    }

    fun setOverrides(conn: Connection, instanceId: String, json: String) {
        conn.prepareStatement(
            "UPDATE instance_launch_overrides SET overrides = jsonb(?) WHERE instance_id = ?"
        ).use { stmt ->
            stmt.setString(1, json)
            stmt.setString(2, instanceId)

            stmt.executeUpdate()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val path = Paths.get(System.getenv("APPDATA"), "ModrinthApp/app.db")
        val agent = JsonPrimitive("-javaagent:C:\\\\Users\\\\evan\\\\IdeaProjects\\\\big_shot_loader\\\\agent\\\\build\\\\libs\\\\agent-1.0.0.jar")

        val database = DriverManager.getConnection("jdbc:sqlite:$path")
        val instances = getInstances(database).sortedWith(Comparator.comparing { it.name })

        val frame = JFrame("Big Shot Mod Loader Installer")

        frame.size = Dimension(400, 800)
        frame.isResizable = false
        frame.setLocationRelativeTo(null)
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                database.close()
                frame.dispose()
            }
        })

        frame.layout = BorderLayout()

        val title = JLabel("Choose which instance(s) to install Big Shot Loader in")
        frame.add(title, BorderLayout.NORTH)

        val versionsPanel = JPanel()
        versionsPanel.layout = BoxLayout(versionsPanel, BoxLayout.Y_AXIS)

        val checks = instances.map {
            val check = JCheckBox(it.name)
            versionsPanel.add(check)
            it to check
        }

        val scrollPane = JScrollPane(versionsPanel)
        frame.add(scrollPane, BorderLayout.CENTER)

        val install = JButton("Install")
        install.addActionListener { e: ActionEvent ->
            if (checks.none { it.second.isSelected }) {
                JOptionPane.showMessageDialog(
                    frame,
                    "Please select at least one profile.",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE
                )
            } else {
                var installed = false

                for ((instance, check) in checks) {
                    if (check.isSelected) {
                        val json = getOverridesJson(database, instance.id)?.let { JsonParser.parseString(it).asJsonObject } ?: JsonObject()

                        val extraLaunchArgs = json["extra_launch_args"]?.asJsonArray ?: JsonArray().also { json.add("extra_launch_args", it) }

                        if (!extraLaunchArgs.contains(agent)) {
                            extraLaunchArgs.add(agent)

                            setOverrides(database, instance.id, Gson().toJson(json))
                            installed = true
                        }
                    }
                }

                if (installed) {
                    JOptionPane.showMessageDialog(
                        frame,
                        "Big Shot Loader installed successfully",
                        "Installation Complete",
                        JOptionPane.INFORMATION_MESSAGE
                    )

                    database.close()
                    frame.dispose()
                } else {
                    JOptionPane.showMessageDialog(
                        frame,
                        "Big Shot Loader was already installed",
                        "Installation Complete",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
        }

        frame.add(install, BorderLayout.SOUTH)

        frame.isVisible = true
    }
}