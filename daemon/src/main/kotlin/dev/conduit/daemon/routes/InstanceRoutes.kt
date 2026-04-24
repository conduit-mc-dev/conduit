package dev.conduit.daemon.routes

import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.core.model.UpdateInstanceRequest
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.store.InstanceStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.instanceRoutes(instanceStore: InstanceStore) {
    route("/api/v1/instances") {
        get {
            call.respond(instanceStore.list())
        }

        post {
            val request = call.receive<CreateInstanceRequest>()
            call.respond(HttpStatusCode.Created, instanceStore.create(request))
        }

        get("/{id}") {
            val id = call.parameters["id"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing instance id")
            call.respond(instanceStore.get(id))
        }

        put("/{id}") {
            val id = call.parameters["id"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing instance id")
            val request = call.receive<UpdateInstanceRequest>()
            call.respond(instanceStore.update(id, request))
        }

        delete("/{id}") {
            val id = call.parameters["id"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing instance id")
            instanceStore.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
