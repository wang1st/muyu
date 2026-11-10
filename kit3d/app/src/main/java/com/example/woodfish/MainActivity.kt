package com.example.woodfish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.node.ModelNode

/**
 * Working pattern from SceneView's official Android quickstart (4.67.x).
 * The GLB stored under assets/models is Y-up and contains a matte PBR material.
 * On-screen touch controls orbit / zoom the 3D mokugyo.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val engine = rememberEngine()
            val modelLoader = rememberModelLoader(engine)
            SceneView(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                cameraManipulator = rememberCameraManipulator()
            ) {
                rememberModelInstance(
                    modelLoader,
                    "models/woodfish_jade_mobile.glb"
                )?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 1.0f,
                    )
                }
            }
        }
    }
}
