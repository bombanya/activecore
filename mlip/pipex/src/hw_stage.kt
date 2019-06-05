package pipex

val OP_STAGE = hwast.hw_opcode("pstage")

class hw_stage(name_in : String, pipeline_in : pipeline) : hwast.hw_exec(OP_STAGE) {
    val name = name_in
    val pipeline = pipeline_in

    fun begin() {
        pipeline.begstage(this)
    }

    fun readremote(remote_local: hw_pipex_var) : hwast.hw_var {
        return pipeline.readremote(this, remote_local)
    }

    fun isactive() : hwast.hw_var {
        return pipeline.isactive(this)
    }

    fun isworking() : hwast.hw_var {
        return pipeline.isworking(this)
    }

    fun isstalled() : hwast.hw_var {
        return pipeline.isstalled(this)
    }

    fun issucc() : hwast.hw_var {
        return pipeline.issucc(this)
    }
}
