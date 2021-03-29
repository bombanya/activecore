/*
 * Pipeline.kt
 *
 *  Created on: 05.06.2019
 *      Author: Alexander Antonov <antonov.alex.alex@gmail.com>
 *     License: See LICENSE file for details
 */

package pipex

import hwast.*

val OP_RD_REMOTE    = hw_opcode("rd_remote")
val OP_ISACTIVE     = hw_opcode("isactive")
val OP_ISWORKING    = hw_opcode("isworking")
val OP_ISSTALLED    = hw_opcode("isstalled")
val OP_ISSUCC       = hw_opcode("issucc")
val OP_ISKILLED     = hw_opcode("iskilled")
val OP_ISFINISHED   = hw_opcode("isfinished")

val OP_PSTALL       = hw_opcode("pstall")
val OP_PKILL        = hw_opcode("pkill")
val OP_PFLUSH       = hw_opcode("pflush")

val OP_ACCUM        = hw_opcode("accum")
val OP_ASSIGN_SUCC  = hw_opcode("assign_succ")
val OP_RD_PREV      = hw_opcode("rd_prev")

enum class PSTAGE_BUSY_MODE {
    BUFFERED, FALL_THROUGH
}

enum class PIPELINE_FC_MODE {
    STALLABLE, CREDIT_BASED
}


open class hw_exec_stage_stat(var stage : hw_stage, opcode : hw_opcode) : hw_exec(opcode)

class hw_exec_read_remote(stage : hw_stage, var remote_var : hw_pipex_var) : hw_exec_stage_stat(stage, OP_RD_REMOTE)

open class pipex_import_expr_context(var_dict : MutableMap<hw_var, hw_var>,
                                     var curStage : hw_stage,
                                     var TranslateInfo: __TranslateInfo,
                                     var curStageInfo : __pstage_info) : import_expr_context(var_dict)

open class Pipeline(val name : String, val pipeline_fc_mode : PIPELINE_FC_MODE) : hw_astc_stdif() {

    override var GenNamePrefix   = "pipex"

    var locals          = ArrayList<hw_var>()
    var globals         = ArrayList<hw_global>()

    var copipe_ifs = mutableMapOf<String, hw_copipe>()
    var mcopipe_ifs = ArrayList<hw_mcopipe_if>()
    var scopipe_ifs = ArrayList<hw_scopipe_if>()

    var copipe_handles = mutableMapOf<String, hw_copipe>()
    var mcopipe_handles = ArrayList<hw_mcopipe_handle>()
    var scopipe_handles = ArrayList<hw_scopipe_handle>()

    var Stages  = mutableMapOf<String, hw_stage>()

    fun stage_handler(name : String, busy_mode : PSTAGE_BUSY_MODE, buf_size_cfg : PSTAGE_BUF_SIZE_CFG) : hw_stage {
        if (FROZEN_FLAG) ERROR("Failed to add stage " + name + ": ASTC frozen")
        var new_stage = hw_stage(name, busy_mode, buf_size_cfg, this)
        if (Stages.put(new_stage.name, new_stage) != null) {
            ERROR("Stage addition problem!")
        }
        return new_stage
    }

    fun stage_handler(name : String, busy_mode : PSTAGE_BUSY_MODE, BUF_SIZE : Int) : hw_stage {
        return stage_handler(name, busy_mode, PSTAGE_BUF_SIZE_CFG(BUF_SIZE))
    }

    fun stage_handler(name : String, busy_mode : PSTAGE_BUSY_MODE) : hw_stage {
        return stage_handler(name, busy_mode, PSTAGE_BUF_SIZE_CFG())
    }

    fun begstage(stage : hw_stage) {
        if (FROZEN_FLAG) ERROR("Failed to begin stage " + stage.name + ": ASTC frozen")
        if (this.size != 0) ERROR("Pipex ASTC inconsistent!")
        // TODO: validate stage presence
        add(stage)
    }

    fun endstage() {
        if (FROZEN_FLAG) ERROR("Failed to end stage: ASTC frozen")
        if (this.size != 1) ERROR("Stage ASTC inconsistent!")
        if (this[0].opcode != OP_STAGE) ERROR("Stage ASTC inconsistent!")
        this.clear()
    }

    private fun add_local(new_local: hw_local) {
        if (FROZEN_FLAG) ERROR("Failed to add local " + new_local.name + ": ASTC frozen")

        if (wrvars.containsKey(new_local.name)) ERROR("Naming conflict for local: " + new_local.name)
        if (rdvars.containsKey(new_local.name)) ERROR("Naming conflict for local: " + new_local.name)

        wrvars.put(new_local.name, new_local)
        rdvars.put(new_local.name, new_local)
        locals.add(new_local)
        new_local.default_astc = this
    }

    fun local(name: String, vartype : hw_type, defimm: hw_imm): hw_local {
        var ret_var = hw_local(name, vartype, defimm)
        add_local(ret_var)
        return ret_var
    }

    fun local(name: String, vartype : hw_type, defval: String): hw_local {
        var ret_var = hw_local(name, vartype, defval)
        add_local(ret_var)
        return ret_var
    }

    fun local(name: String, src_struct_in: hw_struct, dimensions: hw_dim_static): hw_local {
        var ret_var = hw_local(name, hw_type(src_struct_in, dimensions), "0")
        add_local(ret_var)
        return ret_var
    }

    fun local(name: String, src_struct_in: hw_struct): hw_local {
        var ret_var = hw_local(name, hw_type(src_struct_in), "0")
        add_local(ret_var)
        return ret_var
    }

    fun ulocal(name: String, dimensions: hw_dim_static, defimm: hw_imm): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.UNSIGNED, dimensions), defimm)
        add_local(ret_var)
        return ret_var
    }

    fun ulocal(name: String, dimensions: hw_dim_static, defval: String): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.UNSIGNED, dimensions), defval)
        add_local(ret_var)
        return ret_var
    }

    fun ulocal(name: String, msb: Int, lsb: Int, defimm: hw_imm): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.UNSIGNED, msb, lsb), defimm)
        add_local(ret_var)
        return ret_var
    }

    fun ulocal(name: String, msb: Int, lsb: Int, defval: String): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.UNSIGNED, msb, lsb), defval)
        add_local(ret_var)
        return ret_var
    }

    fun ulocal(name: String, defimm: hw_imm): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.UNSIGNED, defimm.imm_value), defimm)
        add_local(ret_var)
        return ret_var
    }

    fun ulocal(name: String, defval: String): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.UNSIGNED, defval), defval)
        add_local(ret_var)
        return ret_var
    }

    fun slocal(name: String, dimensions: hw_dim_static, defimm: hw_imm): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.SIGNED, dimensions), defimm)
        add_local(ret_var)
        return ret_var
    }

    fun slocal(name: String, dimensions: hw_dim_static, defval: String): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.SIGNED, dimensions), defval)
        add_local(ret_var)
        return ret_var
    }

    fun slocal(name: String, msb: Int, lsb: Int, defimm: hw_imm): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.SIGNED, msb, lsb), defimm)
        add_local(ret_var)
        return ret_var
    }

    fun slocal(name: String, msb: Int, lsb: Int, defval: String): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.SIGNED, msb, lsb), defval)
        add_local(ret_var)
        return ret_var
    }

    fun slocal(name: String, defimm: hw_imm): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.SIGNED, defimm.imm_value), defimm)
        add_local(ret_var)
        return ret_var
    }

    fun slocal(name: String, defval: String): hw_local {
        var ret_var = hw_local(name, hw_type(VAR_TYPE.SIGNED, defval), defval)
        add_local(ret_var)
        return ret_var
    }

    private fun add_local_sticky(new_local_sticky: hw_local_sticky) {
        if (FROZEN_FLAG) ERROR("Failed to add local_sticky " + new_local_sticky.name + ": ASTC frozen")

        if (wrvars.containsKey(new_local_sticky.name)) ERROR("Naming conflict for local_sticky: " + new_local_sticky.name)
        if (rdvars.containsKey(new_local_sticky.name)) ERROR("Naming conflict for local_sticky: " + new_local_sticky.name)

        wrvars.put(new_local_sticky.name, new_local_sticky)
        rdvars.put(new_local_sticky.name, new_local_sticky)
        locals.add(new_local_sticky)
        new_local_sticky.default_astc = this
    }

    fun local_sticky(name: String, vartype: hw_type, defimm: hw_imm): hw_local_sticky {
        var ret_var = hw_local_sticky(name, vartype, defimm)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun local_sticky(name: String, vartype: hw_type, defval: String): hw_local_sticky {
        var ret_var = hw_local_sticky(name, vartype, defval)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun local_sticky(name: String, src_struct_in: hw_struct, dimensions: hw_dim_static): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(src_struct_in, dimensions), "0")
        add_local_sticky(ret_var)
        return ret_var
    }

    fun local_sticky(name: String, src_struct_in: hw_struct): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(src_struct_in), "0")
        add_local_sticky(ret_var)
        return ret_var
    }

    fun ulocal_sticky(name: String, dimensions: hw_dim_static, defimm: hw_imm): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.UNSIGNED, dimensions), defimm)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun ulocal_sticky(name: String, dimensions: hw_dim_static, defval: String): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.UNSIGNED, dimensions), defval)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun ulocal_sticky(name: String, msb: Int, lsb: Int, defimm: hw_imm): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.UNSIGNED, msb, lsb), defimm)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun ulocal_sticky(name: String, msb: Int, lsb: Int, defval: String): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.UNSIGNED, msb, lsb), defval)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun ulocal_sticky(name: String, defimm: hw_imm): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.UNSIGNED, defimm.imm_value), defimm)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun ulocal_sticky(name: String, defval: String): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.UNSIGNED, defval), defval)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun slocal_sticky(name: String, dimensions: hw_dim_static, defimm: hw_imm): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.SIGNED, dimensions), defimm)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun slocal_sticky(name: String, dimensions: hw_dim_static, defval: String): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.SIGNED, dimensions), defval)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun slocal_sticky(name: String, msb: Int, lsb: Int, defimm: hw_imm): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.SIGNED, msb, lsb), defimm)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun slocal_sticky(name: String, msb: Int, lsb: Int, defval: String): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.SIGNED, msb, lsb), defval)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun slocal_sticky(name: String, defimm: hw_imm): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.SIGNED, defimm.imm_value), defimm)
        add_local_sticky(ret_var)
        return ret_var
    }

    fun slocal_sticky(name: String, defval: String): hw_local_sticky {
        var ret_var = hw_local_sticky(name, hw_type(VAR_TYPE.SIGNED, defval), defval)
        add_local_sticky(ret_var)
        return ret_var
    }

    private fun add_global(new_global: hw_global) {
        if (FROZEN_FLAG) ERROR("Failed to add global " + new_global.name + ": ASTC frozen")

        if (wrvars.containsKey(new_global.name)) ERROR("Naming conflict for global: " + new_global.name)
        if (rdvars.containsKey(new_global.name)) ERROR("Naming conflict for global: " + new_global.name)

        wrvars.put(new_global.name, new_global)
        rdvars.put(new_global.name, new_global)
        globals.add(new_global)
        new_global.default_astc = this
    }

    fun global(name: String, vartype: hw_type, defimm: hw_imm): hw_global {
        var ret_var = hw_global(name, vartype, defimm)
        add_global(ret_var)
        return ret_var
    }

    fun global(name: String, vartype: hw_type, defval: String): hw_global {
        var ret_var = hw_global(name, vartype, defval)
        add_global(ret_var)
        return ret_var
    }

    fun global(name: String, src_struct_in: hw_struct, dimensions: hw_dim_static): hw_global {
        var ret_var = hw_global(name, hw_type(src_struct_in, dimensions), "0")
        add_global(ret_var)
        return ret_var
    }

    fun global(name: String, src_struct_in: hw_struct): hw_global {
        var ret_var = hw_global(name, hw_type(src_struct_in), "0")
        add_global(ret_var)
        return ret_var
    }

    fun uglobal(name: String, dimensions: hw_dim_static, defimm: hw_imm): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.UNSIGNED, dimensions), defimm)
        add_global(ret_var)
        return ret_var
    }

    fun uglobal(name: String, dimensions: hw_dim_static, defval: String): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.UNSIGNED, dimensions), defval)
        add_global(ret_var)
        return ret_var
    }

    fun uglobal(name: String, msb: Int, lsb: Int, defimm: hw_imm): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.UNSIGNED, msb, lsb), defimm)
        add_global(ret_var)
        return ret_var
    }

    fun uglobal(name: String, msb: Int, lsb: Int, defval: String): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.UNSIGNED, msb, lsb), defval)
        add_global(ret_var)
        return ret_var
    }

    fun uglobal(name: String, defimm: hw_imm): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.UNSIGNED, defimm.imm_value), defimm)
        add_global(ret_var)
        return ret_var
    }

    fun uglobal(name: String, defval: String): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.UNSIGNED, defval), defval)
        add_global(ret_var)
        return ret_var
    }

    fun sglobal(name: String, dimensions: hw_dim_static, defimm: hw_imm): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.SIGNED, dimensions), defimm)
        add_global(ret_var)
        return ret_var
    }

    fun sglobal(name: String, dimensions: hw_dim_static, defval: String): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.SIGNED, dimensions), defval)
        add_global(ret_var)
        return ret_var
    }

    fun sglobal(name: String, msb: Int, lsb: Int, defimm: hw_imm): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.SIGNED, msb, lsb), defimm)
        add_global(ret_var)
        return ret_var
    }

    fun sglobal(name: String, msb: Int, lsb: Int, defval: String): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.SIGNED, msb, lsb), defval)
        add_global(ret_var)
        return ret_var
    }

    fun sglobal(name: String, defimm: hw_imm): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.SIGNED, defimm.imm_value), defimm)
        add_global(ret_var)
        return ret_var
    }

    fun sglobal(name: String, defval: String): hw_global {
        var ret_var = hw_global(name, hw_type(VAR_TYPE.SIGNED, defval), defval)
        add_global(ret_var)
        return ret_var
    }

    private fun add_mcopipe_if(new_mcopipe: hw_mcopipe_if) {
        if (FROZEN_FLAG) ERROR("Failed to add mcopipe_if " + new_mcopipe.name + ": ASTC frozen")
        if (copipe_ifs.put(new_mcopipe.name, new_mcopipe) != null) {
            ERROR("Mcopipe addition problem!")
        }
        mcopipe_ifs.add(new_mcopipe)
    }

    fun mcopipe_if(name: String,
                   wdata_vartype_in: hw_type,
                   rdata_vartype_in: hw_type,
                   trx_id_width_in: Int): hw_mcopipe_if {
        var new_mcopipe = hw_mcopipe_if(this,
            name,
            wdata_vartype_in,
            rdata_vartype_in,
            trx_id_width_in)
        add_mcopipe_if(new_mcopipe)
        return new_mcopipe
    }

    private fun add_mcopipe_handle(new_mcopipe: hw_mcopipe_handle) {
        if (FROZEN_FLAG) ERROR("Failed to add mcopipe_handle " + new_mcopipe.name + ": ASTC frozen")
        if (copipe_handles.put(new_mcopipe.name, new_mcopipe) != null) {
            ERROR("Mcopipe addition problem!")
        }
        mcopipe_handles.add(new_mcopipe)
    }

    fun mcopipe_handle(name: String,
                       wdata_vartype_in: hw_type,
                       rdata_vartype_in: hw_type,
                       trx_id_width_in: Int): hw_mcopipe_handle {
        var new_mcopipe = hw_mcopipe_handle(this,
            name,
            wdata_vartype_in,
            rdata_vartype_in,
            trx_id_width_in)
        add_mcopipe_handle(new_mcopipe)
        return new_mcopipe
    }

    fun mcopipe_handle(mcopipe: hw_mcopipe_if): hw_mcopipe_handle {
        var new_mcopipe = hw_mcopipe_handle(mcopipe)
        add_mcopipe_handle(new_mcopipe)
        return new_mcopipe
    }

    private fun add_scopipe_if(new_scopipe: hw_scopipe_if) {
        if (FROZEN_FLAG) ERROR("Failed to add scopipe_if " + new_scopipe.name + ": ASTC frozen")
        if (copipe_ifs.put(new_scopipe.name, new_scopipe) != null) {
            ERROR("Scopipe addition problem!")
        }
        scopipe_ifs.add(new_scopipe)
    }

    fun scopipe_if(name: String,
                   wdata_vartype_in: hw_type,
                   rdata_vartype_in: hw_type): hw_scopipe_if {
        var new_scopipe = hw_scopipe_if(this,
            name,
            wdata_vartype_in,
            rdata_vartype_in)
        add_scopipe_if(new_scopipe)
        return new_scopipe
    }

    private fun add_scopipe_handle(new_scopipe: hw_scopipe_handle) {
        if (FROZEN_FLAG) ERROR("Failed to add scopipe_handle " + new_scopipe.name + ": ASTC frozen")
        if (copipe_handles.put(new_scopipe.name, new_scopipe) != null) {
            ERROR("Scopipe addition problem!")
        }
        scopipe_handles.add(new_scopipe)
    }

    fun scopipe_handle(name: String,
                       wdata_vartype_in: hw_type,
                       rdata_vartype_in: hw_type): hw_scopipe_handle {
        var new_scopipe = hw_scopipe_handle(this,
            name,
            wdata_vartype_in,
            rdata_vartype_in)
        add_scopipe_handle(new_scopipe)
        return new_scopipe
    }

    fun scopipe_handle(scopipe: hw_scopipe_if): hw_scopipe_handle {
        var new_scopipe = hw_scopipe_handle(scopipe)
        add_scopipe_handle(new_scopipe)
        return new_scopipe
    }

    fun readremote(remote_stage : hw_stage, remote_local: hw_pipex_var) : hw_var {
        var new_expr = hw_exec_read_remote(remote_stage, remote_local)
        var genvar = hw_var(GetGenName("var"), remote_local.vartype, "0")
        remote_stage.AddRdVar(remote_local)
        new_expr.AddWrVar(genvar)
        new_expr.AddGenVar(genvar)
        AddExpr(new_expr)
        return genvar
    }

    fun isactive(remote_stage : hw_stage) : hw_var {
        var new_expr = hw_exec_stage_stat(remote_stage, OP_ISACTIVE)
        var genvar = hw_var(GetGenName("var"), VAR_TYPE.UNSIGNED, 0, 0, "0")
        new_expr.AddWrVar(genvar)
        new_expr.AddGenVar(genvar)
        AddExpr(new_expr)
        return genvar
    }

    fun isworking(remote_stage : hw_stage) : hw_var {
        var new_expr = hw_exec_stage_stat(remote_stage, OP_ISWORKING)
        var genvar = hw_var(GetGenName("var"), VAR_TYPE.UNSIGNED, 0, 0, "0")
        new_expr.AddWrVar(genvar)
        new_expr.AddGenVar(genvar)
        AddExpr(new_expr)
        return genvar
    }

    fun isstalled(remote_stage : hw_stage) : hw_var {
        var new_expr = hw_exec_stage_stat(remote_stage, OP_ISSTALLED)
        var genvar = hw_var(GetGenName("var"), VAR_TYPE.UNSIGNED, 0, 0, "0")
        new_expr.AddWrVar(genvar)
        new_expr.AddGenVar(genvar)
        AddExpr(new_expr)
        return genvar
    }

    fun issucc(remote_stage : hw_stage) : hw_var {
        var new_expr = hw_exec_stage_stat(remote_stage, OP_ISSUCC)
        var genvar = hw_var(GetGenName("var"), VAR_TYPE.UNSIGNED, 0, 0, "0")
        new_expr.AddWrVar(genvar)
        new_expr.AddGenVar(genvar)
        AddExpr(new_expr)
        return genvar
    }

    fun pstall() {
        AddExpr(hw_exec(OP_PSTALL))
    }

    fun pkill() {
        AddExpr(hw_exec(OP_PKILL))
    }

    fun pflush() {
        AddExpr(hw_exec(OP_PFLUSH))
    }

    fun assign_succ(depow_fractions: hw_fracs, tgt : hw_pipex_var, src: hw_param) {
        var new_expr = hw_exec(OP_ASSIGN_SUCC)
        new_expr.AddWrVar(tgt)
        new_expr.AddRdParam(src)
        new_expr.assign_tgt_fractured = hw_fractured(tgt, depow_fractions)
        AddExpr(new_expr)
    }

    fun assign_succ(depow_fractions: hw_fracs, tgt : hw_pipex_var, src: Int) {
        assign_succ(depow_fractions, tgt, hw_imm(src))
    }

    fun assign_succ(tgt : hw_pipex_var, src: hw_param) {
        assign_succ(hw_fracs(), tgt, src)
    }

    fun assign_succ(tgt : hw_pipex_var, src: Int) {
        assign_succ(tgt, hw_imm(src))
    }

    fun accum(depow_fractions: hw_fracs, tgt : hw_local, src: hw_param) {
        var new_expr = hw_exec(OP_ACCUM)
        new_expr.AddWrVar(tgt)
        new_expr.AddRdParam(src)
        new_expr.assign_tgt_fractured = hw_fractured(tgt, depow_fractions)
        AddExpr(new_expr)
    }

    fun accum(depow_fractions: hw_fracs, tgt : hw_local, src: Int) {
        accum(depow_fractions, tgt, hw_imm(src))
    }

    fun accum(tgt : hw_local, src: hw_param) {
        accum(hw_fracs(), tgt, src)
    }

    fun accum(tgt : hw_local, src: Int) {
        accum(tgt, hw_imm(src))
    }

    fun readprev(src : hw_pipex_var) : hw_pipex_var {
        var new_expr = hw_exec(OP_RD_PREV)
        var genvar = hw_pipex_var(GetGenName("readprev"), src.vartype, src.defimm)
        genvar.default_astc = this
        new_expr.AddRdVar(src)
        new_expr.AddGenVar(genvar)
        new_expr.AddWrVar(genvar)
        AddExpr(new_expr)
        return genvar
    }

    fun mcopipe_req(mcopipe_if : hw_mcopipe_if, mcopipe_handle : hw_mcopipe_handle, cmd : hw_param, wdata : hw_param) : hw_var {
        var new_expr = hw_exec_mcopipe_req(mcopipe_if, mcopipe_handle)
        var genvar = hw_var(GetGenName("mcopipe_req_rdy"), VAR_TYPE.UNSIGNED, 0, 0, "0")
        new_expr.AddRdParam(cmd)
        new_expr.AddRdParam(wdata)
        new_expr.AddWrVar(genvar)
        new_expr.AddGenVar(genvar)
        AddExpr(new_expr)
        return genvar
    }

    fun mcopipe_resp(mcopipe_handle : hw_mcopipe_handle, rdata : hw_var) : hw_var {
        var new_expr = hw_exec_mcopipe_resp(mcopipe_handle)
        var genvar = hw_var(GetGenName("mcopipe_resp_rdy"), VAR_TYPE.UNSIGNED, 0, 0, "0")
        new_expr.AddWrVar(genvar)
        new_expr.AddGenVar(genvar)
        new_expr.AddWrVar(rdata)
        AddExpr(new_expr)
        return genvar
    }

    fun scopipe_req(scopipe_if : hw_scopipe_if, scopipe_handle : hw_scopipe_handle, cmd : hw_var, rdata : hw_var) : hw_var {
        var new_expr = hw_exec_scopipe_req(scopipe_if, scopipe_handle)
        var genvar = hw_var(GetGenName("scopipe_req_rdy"), VAR_TYPE.UNSIGNED, 0, 0, "0")
        new_expr.AddWrVar(cmd)
        new_expr.AddWrVar(rdata)
        new_expr.AddWrVar(genvar)
        new_expr.AddGenVar(genvar)
        AddExpr(new_expr)
        return genvar
    }

    fun scopipe_resp(scopipe_handle : hw_scopipe_handle, wdata : hw_param) : hw_var {
        var new_expr = hw_exec_scopipe_resp(scopipe_handle)
        var genvar = hw_var(GetGenName("scopipe_resp_rdy"), VAR_TYPE.UNSIGNED, 0, 0, "0")
        new_expr.AddRdParam(wdata)
        new_expr.AddWrVar(genvar)
        new_expr.AddGenVar(genvar)
        AddExpr(new_expr)
        return genvar
    }

    fun validate() {
        if ((pipeline_fc_mode == PIPELINE_FC_MODE.CREDIT_BASED) && (Stages.size < 3)) ERROR("Can't make credit-based mechanism for stage number < 3")
        for (wrvar in wrvars) {
            if (!wrvar.value.write_done) WARNING("signal " + wrvar.value.name + " is not initialized")
        }
        for (rdvar in rdvars) {
            if (!rdvar.value.read_done) WARNING("signal " + rdvar.value.name + " is not used!")
        }
    }

    fun ProcessSyncOp(expression : hw_exec, Translate_info : __TranslateInfo, pstage_info : __pstage_info, cyclix_gen : cyclix.Generic) {
        if ((expression.opcode == OP1_IF) || (expression.opcode == OP1_WHILE)) {
            for (subexpression in expression.expressions) ProcessSyncOp(subexpression, Translate_info, pstage_info, cyclix_gen)

        } else if (expression.opcode == OP_ACCUM) {
            if (!pstage_info.accum_tgts.contains(expression.wrvars[0])) {
                pstage_info.accum_tgts.add(expression.wrvars[0])
            }

        } else if (expression.opcode == OP_ASSIGN_SUCC) {
            if (!pstage_info.assign_succ_assocs.containsKey(expression.wrvars[0])) {
                val req = cyclix_gen.ulocal(GetGenName("succreq"), 0, 0, "0")
                val buf = cyclix_gen.local(GetGenName("succbuf"), expression.wrvars[0].vartype, expression.wrvars[0].defimm)
                pstage_info.assign_succ_assocs.put((expression.wrvars[0] as hw_pipex_var), __assign_buf(req, buf))
            }

        } else if (expression.opcode == OP_RD_REMOTE) {
            (expression as hw_exec_read_remote).stage.AddRdVar(expression.remote_var)

        } else if (expression.opcode == OP_MCOPIPE_REQ) {
            if (!pstage_info.mcopipe_handle_reqs.contains((expression as hw_exec_mcopipe_req).mcopipe_handle)) {
                pstage_info.mcopipe_handle_reqs.add(expression.mcopipe_handle)
            }

        } else if (expression.opcode == OP_MCOPIPE_RESP) {
            if (!pstage_info.mcopipe_handle_resps.contains((expression as hw_exec_mcopipe_resp).mcopipe_handle))
                pstage_info.mcopipe_handle_resps.add(expression.mcopipe_handle)

        } else if (expression.opcode == OP_SCOPIPE_REQ) {
            if (!pstage_info.scopipe_handle_reqs.contains((expression as hw_exec_scopipe_req).scopipe_handle))
                pstage_info.scopipe_handle_reqs.add(expression.scopipe_handle)

        } else if (expression.opcode == OP_SCOPIPE_RESP) {
            if (!pstage_info.scopipe_handle_resps.contains((expression as hw_exec_scopipe_resp).scopipe_handle))
                pstage_info.scopipe_handle_resps.add(expression.scopipe_handle)

        }
    }

    fun FillMcopipeReqDict(expression : hw_exec, TranslateInfo : __TranslateInfo) {
        if ((expression.opcode == OP1_IF) || (expression.opcode == OP1_WHILE)) {
            for (subexpression in expression.expressions) FillMcopipeReqDict(subexpression, TranslateInfo)
        } else if (expression.opcode == OP_MCOPIPE_REQ) {
            var mcopipe_ArrayList = TranslateInfo.__mcopipe_handle_reqdict[(expression as hw_exec_mcopipe_req).mcopipe_handle]!!
            if (!mcopipe_ArrayList.contains(expression.mcopipe_if)) mcopipe_ArrayList.add(expression.mcopipe_if)
        }
    }

    fun FillScopipeReqDict(expression : hw_exec, TranslateInfo : __TranslateInfo) {
        if ((expression.opcode == OP1_IF) || (expression.opcode == OP1_WHILE)) {
            for (subexpression in expression.expressions) FillScopipeReqDict(subexpression, TranslateInfo)
        } else if (expression.opcode == OP_SCOPIPE_REQ) {
            var scopipe_ArrayList = TranslateInfo.__scopipe_handle_reqdict[(expression as hw_exec_scopipe_req).scopipe_handle]!!
            if (!scopipe_ArrayList.contains(expression.scopipe_if)) scopipe_ArrayList.add(expression.scopipe_if)
        }
    }

    fun reconstruct_expression(DEBUG_FLAG : Boolean,
                               cyclix_gen : hw_astc,
                               expr : hw_exec,
                               context : import_expr_context) {

        cyclix_gen as cyclix.Generic
        context as pipex_import_expr_context

        var fractions = ReconstructFractions(expr.assign_tgt_fractured.depow_fractions, context.curStageInfo.var_dict)

        if (DEBUG_FLAG) {
            MSG(DEBUG_FLAG, "Reconstructing expression: " + expr.opcode.default_string)
            for (param in expr.params) MSG(DEBUG_FLAG, "param: " + param.GetString())
        }

        if ((expr.opcode == OP1_ASSIGN)) {

            if (expr.wrvars[0] is hw_global) {
                cyclix_gen.begif(!context.curStageInfo.pctrl_stalled_glbl)
                run {
                    cyclix_gen.begif(context.curStageInfo.pctrl_active_glbl)
                    run {
                        cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), fractions, context.curStageInfo.TranslateParam(expr.params[0]))
                    }; cyclix_gen.endif()
                }; cyclix_gen.endif()
            } else {
                cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), fractions, context.curStageInfo.TranslateParam(expr.params[0]))
            }

        } else if (expr.opcode == OP_PKILL) {
            if (context.TranslateInfo.pipeline.pipeline_fc_mode == PIPELINE_FC_MODE.CREDIT_BASED) {
                var curStageIndex = context.TranslateInfo.StageList.indexOf(context.curStage)
                if ((curStageIndex != 0) && (curStageIndex != context.TranslateInfo.StageList.lastIndex)) {
                    ERROR("Attempting to kill transaction with credit-based mechanism at stage " + context.curStage.name)
                }
            }
            context.curStageInfo.pkill_cmd_internal(cyclix_gen)

        } else if (expr.opcode == OP_PSTALL) {
            if (context.TranslateInfo.pipeline.pipeline_fc_mode == PIPELINE_FC_MODE.CREDIT_BASED) {
                var curStageIndex = context.TranslateInfo.StageList.indexOf(context.curStage)
                if ((curStageIndex != 0) && (curStageIndex != context.TranslateInfo.StageList.lastIndex)) {
                    ERROR("Attempting to stall transaction with credit-based mechanism at stage " + context.curStage.name)
                }
            }
            context.curStageInfo.pstall_ifactive_cmd(cyclix_gen)

        } else if (expr.opcode == OP_PFLUSH) {
            if (context.TranslateInfo.pipeline.pipeline_fc_mode == PIPELINE_FC_MODE.CREDIT_BASED) {
                ERROR("Attempting to slush pipeline with credit-based mechanism at stage " + context.curStage.name)
            }
            context.curStageInfo.pflush_cmd_internal(cyclix_gen)

        } else if (expr.opcode == OP_RD_PREV) {
            cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), fractions, context.TranslateInfo.__global_assocs[expr.rdvars[0]]!!.cyclix_global_buf)

        } else if (expr.opcode == OP_ACCUM) {
            cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), fractions, context.curStageInfo.TranslateParam(expr.params[0]))
            cyclix_gen.begif(context.curStageInfo.pctrl_active_glbl)
            run {
                var fracs = hw_fracs(0)
                fracs.add(hw_frac_SubStruct(expr.wrvars[0].name))
                cyclix_gen.assign(context.curStageInfo.TRX_BUF, fracs, context.curStageInfo.TranslateParam(expr.params[0]))
            }; cyclix_gen.endif()

        } else if (expr.opcode == OP_ASSIGN_SUCC) {
            cyclix_gen.assign(context.curStageInfo.assign_succ_assocs[expr.wrvars[0]]!!.req, 1)
            cyclix_gen.assign(context.curStageInfo.assign_succ_assocs[expr.wrvars[0]]!!.buf, fractions, context.curStageInfo.TranslateParam(expr.params[0]))

        } else if (expr.opcode == OP_RD_REMOTE) {
            cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), fractions, context.TranslateInfo.__stage_assocs[(expr as hw_exec_read_remote).stage]!!.TranslateVar(expr.remote_var))

        } else if (expr.opcode == OP_ISACTIVE) {
            cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), context.TranslateInfo.__stage_assocs[(expr as hw_exec_stage_stat).stage]!!.pctrl_active_glbl)

        } else if (expr.opcode == OP_ISWORKING) {
            cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), context.TranslateInfo.__stage_assocs[(expr as hw_exec_stage_stat).stage]!!.pctrl_working)

        } else if (expr.opcode == OP_ISSTALLED) {
            cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), context.TranslateInfo.__stage_assocs[(expr as hw_exec_stage_stat).stage]!!.pctrl_stalled_glbl)

        } else if (expr.opcode == OP_ISSUCC) {
            cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), context.TranslateInfo.__stage_assocs[(expr as hw_exec_stage_stat).stage]!!.pctrl_succ)

        } else if (expr.opcode == OP_ISKILLED) {
            cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), context.TranslateInfo.__stage_assocs[(expr as hw_exec_stage_stat).stage]!!.pctrl_killed_glbl)

        } else if (expr.opcode == OP_ISFINISHED) {
            cyclix_gen.assign(
                context.curStageInfo.TranslateVar(expr.wrvars[0]),
                context.TranslateInfo.__stage_assocs[(expr as hw_exec_stage_stat).stage]!!.pctrl_finish
            )

        } else if (expr.opcode == OP_FIFO_WR_UNBLK) {
            var wdata_translated = context.curStageInfo.TranslateParam(expr.params[0])
            var ack_translated = context.curStageInfo.TranslateVar(expr.wrvars[0])
            var fifo_wr_translated = context.TranslateInfo.__fifo_wr_assocs[(expr as hw_exec_fifo_wr_unblk).fifo]!!
            cyclix_gen.begif(context.curStageInfo.pctrl_active_glbl)
            run {
                cyclix_gen.assign(ack_translated, cyclix_gen.fifo_wr_unblk(fifo_wr_translated, wdata_translated))
            }; cyclix_gen.endif()

        } else if (expr.opcode == OP_FIFO_RD_UNBLK) {
            var ack_translated = context.curStageInfo.TranslateVar(expr.wrvars[0])
            var rdata_translated = context.curStageInfo.TranslateVar(expr.wrvars[1])
            var fifo_rd_translated = context.TranslateInfo.__fifo_rd_assocs[(expr as hw_exec_fifo_rd_unblk).fifo]!!
            cyclix_gen.begif(context.curStageInfo.pctrl_active_glbl)
            run {
                cyclix_gen.assign(ack_translated, cyclix_gen.fifo_rd_unblk(fifo_rd_translated, rdata_translated))
            }; cyclix_gen.endif()

        } else if (expr.opcode == OP_MCOPIPE_REQ) {

            var mcopipe_if              = (expr as hw_exec_mcopipe_req).mcopipe_if
            var mcopipe_if_assoc        = context.TranslateInfo.__mcopipe_if_assocs[mcopipe_if]!!
            var mcopipe_handle          = expr.mcopipe_handle
            var mcopipe_handle_assoc    = context.TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!

            var rdreq_pending_translated    = context.curStageInfo.TranslateVar(mcopipe_handle_assoc.rdreq_pending)
            var tid_translated              = context.curStageInfo.TranslateVar(mcopipe_handle_assoc.tid)
            var if_id_translated            = context.curStageInfo.TranslateVar(mcopipe_handle_assoc.if_id)

            cyclix_gen.begif(context.curStageInfo.pctrl_active_glbl)
            run {

                cyclix_gen.begif(cyclix_gen.bnot(mcopipe_if_assoc.full_flag))
                run {

                    // translating params
                    var cmd_translated      = context.curStageInfo.TranslateParam(expr.params[0])
                    var wdata_translated    = context.curStageInfo.TranslateParam(expr.params[1])

                    // finding id
                    var handle_id = context.TranslateInfo.__mcopipe_handle_reqdict[mcopipe_handle]!!.indexOf(expr.mcopipe_if)

                    var req_struct = cyclix_gen.local(GetGenName("req_struct"),
                        mcopipe_if_assoc.req_fifo.vartype,
                        mcopipe_if_assoc.req_fifo.defimm)

                    cyclix_gen.assign(req_struct, hw_fracs("we"), cmd_translated)
                    cyclix_gen.assign(req_struct, hw_fracs("wdata"), wdata_translated)

                    cyclix_gen.begif(cyclix_gen.fifo_wr_unblk(mcopipe_if_assoc.req_fifo, req_struct))
                    run {

                        // req management
                        cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), 1)
                        cyclix_gen.assign(rdreq_pending_translated, cyclix_gen.lnot(cmd_translated))
                        cyclix_gen.assign(tid_translated, mcopipe_if_assoc.wr_ptr)
                        cyclix_gen.assign(if_id_translated, hw_imm(mcopipe_handle_assoc.if_id.vartype.dimensions, handle_id.toString()))

                        // mcopipe wr done
                        cyclix_gen.begif(rdreq_pending_translated)
                        run {
                            cyclix_gen.assign(mcopipe_if_assoc.wr_done, 1)
                        }; cyclix_gen.endif()

                    }; cyclix_gen.endif()

                }; cyclix_gen.endif()

            }; cyclix_gen.endif()

        } else if (expr.opcode == OP_MCOPIPE_RESP) {

            var mcopipe_handle          = (expr as hw_exec_mcopipe_resp).mcopipe_handle
            var mcopipe_handle_assoc    = context.TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!

            var resp_done_translated    = context.curStageInfo.TranslateVar(mcopipe_handle_assoc.resp_done)
            var rdata_translated        = context.curStageInfo.TranslateVar(mcopipe_handle_assoc.rdata)

            cyclix_gen.begif(resp_done_translated)
            run {
                cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[1]), rdata_translated)
            }; cyclix_gen.endif()

            cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), resp_done_translated)

        } else if (expr.opcode == OP_SCOPIPE_REQ) {

            var scopipe_if              = (expr as hw_exec_scopipe_req).scopipe_if
            var scopipe_if_assoc        = context.TranslateInfo.__scopipe_if_assocs[scopipe_if]!!
            var scopipe_handle          = expr.scopipe_handle
            var scopipe_handle_assoc    = context.TranslateInfo.__scopipe_handle_assocs[scopipe_handle]!!

            var if_id_translated        = context.curStageInfo.TranslateVar(scopipe_handle_assoc.if_id)
            var we_translated           = context.curStageInfo.TranslateVar(scopipe_handle_assoc.we)

            // finding id
            var handle_id = context.TranslateInfo.__scopipe_handle_reqdict[scopipe_handle]!!.indexOf(expr.scopipe_if)

            cyclix_gen.begif(context.curStageInfo.pctrl_active_glbl)
            run {

                var req_struct = cyclix_gen.local(GetGenName("req_struct"),
                    scopipe_if_assoc.req_fifo.vartype,
                    scopipe_if_assoc.req_fifo.defimm)

                cyclix_gen.begif(cyclix_gen.fifo_rd_unblk(scopipe_if_assoc.req_fifo, req_struct))
                run {
                    cyclix_gen.subStruct_gen(context.curStageInfo.TranslateVar(expr.wrvars[0]),  req_struct, "we")
                    cyclix_gen.subStruct_gen(context.curStageInfo.TranslateVar(expr.wrvars[1]),  req_struct, "wdata")
                    cyclix_gen.assign(if_id_translated, hw_imm(scopipe_handle_assoc.if_id.vartype.dimensions, handle_id.toString()))

                    cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[2]), 1)
                }; cyclix_gen.endif()
            }; cyclix_gen.endif()

        } else if (expr.opcode == OP_SCOPIPE_RESP) {

            var scopipe_handle          = (expr as hw_exec_scopipe_resp).scopipe_handle
            var scopipe_handle_assoc    = context.TranslateInfo.__scopipe_handle_assocs[scopipe_handle]!!

            var if_id_translated        = context.curStageInfo.TranslateVar(scopipe_handle_assoc.if_id)
            var we_translated           = context.curStageInfo.TranslateVar(scopipe_handle_assoc.we)

            cyclix_gen.begif(context.curStageInfo.pctrl_active_glbl)
            run {
                for (scopipe_if in context.TranslateInfo.__scopipe_handle_reqdict[scopipe_handle]!!) {
                    var scopipe_if_assoc        = context.TranslateInfo.__scopipe_if_assocs[scopipe_if]!!

                    cyclix_gen.begif(cyclix_gen.eq2(if_id_translated, context.TranslateInfo.__scopipe_handle_reqdict[scopipe_handle]!!.indexOf(scopipe_if)))
                    run {
                        cyclix_gen.assign(context.curStageInfo.TranslateVar(expr.wrvars[0]), cyclix_gen.fifo_wr_unblk(scopipe_if_assoc.resp_fifo, context.curStageInfo.TranslateVar(expr.rdvars[0])))
                    }; cyclix_gen.endif()
                }
            }; cyclix_gen.endif()

        } else cyclix_gen.import_expr(DEBUG_FLAG, expr, context, ::reconstruct_expression)
    }

    fun check_bufsize(stage : hw_stage, actual_bufsize: Int) {
        if ((stage.BUF_SIZE.cfg_mode == PSTAGE_BUF_SIZE_CFG_MODE.EXACT) && (stage.BUF_SIZE.SIZE != actual_bufsize)) {
            WARNING("Overriding pstage buffer size for stage " + stage.name + ", given: " + stage.BUF_SIZE.SIZE + ", actual: " + actual_bufsize)
            throw Exception()
        }
    }

    fun translate_to_cyclix(DEBUG_FLAG : Boolean) : cyclix.Generic {

        MSG("Translating to cyclix: beginning")
        validate()

        var cyclix_gen = cyclix.Generic(name)
        var TranslateInfo = __TranslateInfo(this)

        MSG(DEBUG_FLAG, "Processing globals")
        for (global in globals) {
            var new_global = cyclix_gen.global(("genpsticky_glbl_" + global.name), global.vartype, global.defimm)
            var new_global_buf = cyclix_gen.local(("genpsticky_glbl_" + global.name + "_buf"), global.vartype, global.defimm)
            TranslateInfo.__global_assocs.put(global, __global_info(new_global, new_global_buf))
        }

        // Processing FIFOs
        for (fifo_out in fifo_outs) {
            var new_fifo_out = cyclix_gen.fifo_out(fifo_out.name, fifo_out.vartype)
            TranslateInfo.__fifo_wr_assocs.put(fifo_out, new_fifo_out)
        }
        for (fifo_in in fifo_ins) {
            var new_fifo_in = cyclix_gen.fifo_in(fifo_in.name, fifo_in.vartype)
            TranslateInfo.__fifo_rd_assocs.put(fifo_in, new_fifo_in)
        }

        // Generating mcopipes' resources //
        MSG(DEBUG_FLAG, "Generating mcopipes if resources")
        for (mcopipe_if in mcopipe_ifs) {
            val mcopipe_name_prefix = "genmcopipe_" + mcopipe_if.name + "_"

            var wr_done     = cyclix_gen.uglobal((mcopipe_name_prefix + "wr_done"), 0, 0, "0")
            var rd_done     = cyclix_gen.uglobal((mcopipe_name_prefix + "rd_done"), 0, 0, "0")
            var full_flag   = cyclix_gen.uglobal((mcopipe_name_prefix + "full_flag"), 0, 0, "0")
            var empty_flag  = cyclix_gen.uglobal((mcopipe_name_prefix + "empty_flag"), 0, 0, "1")
            var wr_ptr      = cyclix_gen.uglobal((mcopipe_name_prefix + "wr_ptr"), (mcopipe_if.trx_id_width-1), 0, "0")
            var rd_ptr      = cyclix_gen.uglobal((mcopipe_name_prefix + "rd_ptr"), (mcopipe_if.trx_id_width-1), 0, "0")
            var wr_ptr_next = cyclix_gen.ulocal((mcopipe_name_prefix + "wr_ptr_next"), (mcopipe_if.trx_id_width-1), 0, "0")
            var rd_ptr_next = cyclix_gen.ulocal((mcopipe_name_prefix + "rd_ptr_next"), (mcopipe_if.trx_id_width-1), 0, "0")

            var wr_struct = hw_struct("genpmodule_" + name + "_" + mcopipe_name_prefix + "genstruct_fifo_wdata")
            wr_struct.addu("we", 0, 0, "0")
            wr_struct.add("wdata", mcopipe_if.wdata_vartype, "0")

            var req_fifo = cyclix_gen.fifo_out((mcopipe_name_prefix + "req"), wr_struct)
            var resp_fifo = cyclix_gen.fifo_in((mcopipe_name_prefix + "resp"), mcopipe_if.rdata_vartype)

            TranslateInfo.__mcopipe_if_assocs.put(mcopipe_if, __mcopipe_if_info(
                wr_done,
                rd_done,
                full_flag,
                empty_flag,
                wr_ptr,
                rd_ptr,
                wr_ptr_next,
                rd_ptr_next,
                req_fifo,
                resp_fifo))
        }

        // Generating mcopipe handles' resources //
        MSG(DEBUG_FLAG, "Generating mcopipe handles' resources")
        for (mcopipe_handle in mcopipe_handles) {
            TranslateInfo.__mcopipe_handle_reqdict.put(mcopipe_handle, ArrayList<hw_mcopipe_if>())
        }
        for (stage in Stages) {
            for (expression in stage.value.expressions) {
                FillMcopipeReqDict(expression, TranslateInfo)
            }
        }
        for (mcopipe_handle in mcopipe_handles) {
            val name_prefix = "genmcopipe_handle_" + mcopipe_handle.name + "_genvar_"

            var if_id           = ulocal_sticky((name_prefix + "if_id"), GetWidthToContain(TranslateInfo.__mcopipe_handle_reqdict[mcopipe_handle]!!.size)-1, 0, "0")
            var resp_done       = ulocal_sticky((name_prefix + "resp_done"), 0, 0, "0")
            var rdata           =  local_sticky((name_prefix + "rdata"), mcopipe_handle.rdata_vartype, "0")
            var rdreq_pending   = ulocal_sticky((name_prefix + "rdreq_pending"), 0, 0, "0")
            var tid             = ulocal_sticky((name_prefix + "tid"), (mcopipe_handle.trx_id_width-1), 0, "0")

            TranslateInfo.__mcopipe_handle_assocs.put(mcopipe_handle, __mcopipe_handle_info(
                if_id,
                resp_done,
                rdata,
                rdreq_pending,
                tid))
        }

        // Generating scopipes' resources //
        MSG(DEBUG_FLAG, "Generating scopipes' resources")
        for (scopipe_if in scopipe_ifs) {
            val scopipe_name_prefix = "genscopipe_" + scopipe_if.name + "_"

            var rd_struct = hw_struct("genpmodule_" + name + "_" + scopipe_name_prefix + "genstruct_fifo_wdata")
            rd_struct.addu("we", 0, 0, "0")
            rd_struct.add("wdata", scopipe_if.wdata_vartype, "0")

            var req_fifo = cyclix_gen.fifo_in((scopipe_name_prefix + "req"), rd_struct)
            var resp_fifo = cyclix_gen.fifo_out((scopipe_name_prefix + "resp"), scopipe_if.rdata_vartype)

            TranslateInfo.__scopipe_if_assocs.put(scopipe_if, __scopipe_if_info(
                req_fifo,
                resp_fifo))
        }

        // Generating scopipe handles' resources //
        MSG(DEBUG_FLAG, "Generating scopipe handles' resources")
        for (scopipe_handle in scopipe_handles) {
            TranslateInfo.__scopipe_handle_reqdict.put(scopipe_handle, ArrayList<hw_scopipe_if>())
        }
        for (stage in Stages) {
            for (expression in stage.value.expressions) {
                FillScopipeReqDict(expression, TranslateInfo)
            }
        }
        for (scopipe_handle in scopipe_handles) {
            val name_prefix = "genscopipe_handle_" + scopipe_handle.name + "_genvar_"

            var if_id           = ulocal_sticky((name_prefix + "if_id"), GetWidthToContain(TranslateInfo.__scopipe_handle_reqdict[scopipe_handle]!!.size)-1, 0, "0")
            var we              = ulocal_sticky((name_prefix + "we"), 0, 0, "0")

            TranslateInfo.__scopipe_handle_assocs.put(scopipe_handle, __scopipe_handle_info(
                if_id,
                we))
        }

        freeze()

        // Put stages in ArrayLists
        for (stage in Stages) TranslateInfo.StageList.add(stage.value)

        // Analyzing sync operations //
        MSG(DEBUG_FLAG, "Distributing synchronization primitives by pstages")

        for (CUR_STAGE_INDEX in 0 until TranslateInfo.StageList.size) {
            val stage = TranslateInfo.StageList[CUR_STAGE_INDEX]
            val name_prefix = "genpstage_" + stage.name + "_"

            var pctrl_new           = cyclix_gen.ulocal((name_prefix + "genpctrl_new"), 0, 0, "0")
            var pctrl_working       = cyclix_gen.ulocal((name_prefix + "genpctrl_working"), 0, 0, "0")
            var pctrl_succ          = cyclix_gen.ulocal((name_prefix + "genpctrl_succ"), 0, 0, "0")
            var pctrl_occupied      = cyclix_gen.ulocal((name_prefix + "genpctrl_occupied"), 0, 0, "0")
            var pctrl_finish        = cyclix_gen.ulocal((name_prefix + "genpctrl_finish"), 0, 0, "0")
            var pctrl_flushreq      = cyclix_gen.ulocal((name_prefix + "genpctrl_flushreq"), 0, 0, "0")
            var pctrl_nevictable    = cyclix_gen.ulocal((name_prefix + "genpctrl_nevictable"), 0, 0, "0")
            var pctrl_rdy           = cyclix_gen.ulocal((name_prefix + "genpctrl_rdy"), 0, 0, "0")

            var pctrl_active_glbl   = cyclix_gen.uglobal((name_prefix + "genpctrl_active_glbl"), 0, 0, "0")
            var pctrl_stalled_glbl  = cyclix_gen.uglobal((name_prefix + "genpctrl_stalled_glbl"), 0, 0, "0")
            var pctrl_killed_glbl   = cyclix_gen.uglobal((name_prefix + "genpctrl_killed_glbl"), 0, 0, "0")

            var pstage_buf_size = 1
            if (CUR_STAGE_INDEX == 0) {
                check_bufsize(stage, 1)
                pstage_buf_size = 1
            } else {
                if (pipeline_fc_mode == PIPELINE_FC_MODE.CREDIT_BASED) {
                    if (CUR_STAGE_INDEX == TranslateInfo.StageList.lastIndex) {
                        check_bufsize(stage, TranslateInfo.StageList.size-1)
                        pstage_buf_size = TranslateInfo.StageList.size-1
                    } else {
                        check_bufsize(stage, 1)
                        pstage_buf_size = 1
                    }
                } else {
                    if (stage.BUF_SIZE.cfg_mode == PSTAGE_BUF_SIZE_CFG_MODE.EXACT) {
                        pstage_buf_size = stage.BUF_SIZE.SIZE
                    } else {
                        pstage_buf_size = 1
                    }
                }
            }

            var pstage_info = __pstage_info(TranslateInfo,
                name_prefix,
                pstage_buf_size,
                pctrl_new,
                pctrl_working,
                pctrl_succ,
                pctrl_occupied,
                pctrl_finish,
                pctrl_flushreq,
                pctrl_nevictable,
                pctrl_rdy,
                pctrl_active_glbl,
                pctrl_stalled_glbl,
                pctrl_killed_glbl)

            TranslateInfo.__stage_assocs.put(stage, pstage_info)
            for (expression in stage.expressions) {
                ProcessSyncOp(expression, TranslateInfo, pstage_info, cyclix_gen)
            }
        }
        if (pipeline_fc_mode == PIPELINE_FC_MODE.CREDIT_BASED) TranslateInfo.gencredit_counter = cyclix_gen.uglobal("gencredit_counter", GetWidthToContain(Stages.size)-1, 0, "0")

        // Generating resources //
        MSG(DEBUG_FLAG, "Generating resources")

        // Put stages info in ArrayLists
        for (stageAssoc in TranslateInfo.__stage_assocs) TranslateInfo.StageInfoList.add(stageAssoc.value)

        MSG(DEBUG_FLAG, "Processing genvars")
        for (CUR_STAGE_INDEX in 0 until TranslateInfo.StageList.size) {
            for (genvar in TranslateInfo.StageList[CUR_STAGE_INDEX].genvars) {
                var genvar_local = cyclix_gen.local(GetGenName("var"), genvar.vartype, genvar.defimm)
                TranslateInfo.StageInfoList[CUR_STAGE_INDEX].pContext_local_dict.put(genvar, genvar_local)
            }
        }

        // Generate resources for (m/s)copipes
        for (CUR_STAGE_INDEX in 0 until TranslateInfo.StageList.size) {

            var curStage = TranslateInfo.StageList[CUR_STAGE_INDEX]
            var curStageInfo = TranslateInfo.StageInfoList[CUR_STAGE_INDEX]

            var prev_req_mcopipelist    = ArrayList<hw_mcopipe_handle>()
            var cur_req_mcopipelist     = ArrayList<hw_mcopipe_handle>()
            var cur_resp_mcopipelist    = ArrayList<hw_mcopipe_handle>()
            var next_resp_mcopipelist   = ArrayList<hw_mcopipe_handle>()

            var prev_req_scopipelist    = ArrayList<hw_scopipe_handle>()
            var cur_req_scopipelist     = ArrayList<hw_scopipe_handle>()
            var cur_resp_scopipelist    = ArrayList<hw_scopipe_handle>()
            var next_resp_scopipelist   = ArrayList<hw_scopipe_handle>()

            for (STAGE_INDEX_ANLZ in 0 until TranslateInfo.StageList.size) {

                var curStageAnlz = TranslateInfo.StageList[STAGE_INDEX_ANLZ]
                var curStageAnlz_info = TranslateInfo.StageInfoList[STAGE_INDEX_ANLZ]

                if (STAGE_INDEX_ANLZ < CUR_STAGE_INDEX) {
                    prev_req_mcopipelist =
                        UniteArrayLists(prev_req_mcopipelist, curStageAnlz_info.mcopipe_handle_reqs)
                    prev_req_scopipelist =
                        UniteArrayLists(prev_req_scopipelist, curStageAnlz_info.scopipe_handle_reqs)

                } else if (STAGE_INDEX_ANLZ == CUR_STAGE_INDEX) {
                    cur_req_mcopipelist =
                        UniteArrayLists(cur_req_mcopipelist, curStageAnlz_info.mcopipe_handle_reqs)
                    cur_resp_mcopipelist =
                        UniteArrayLists(cur_resp_mcopipelist, curStageAnlz_info.mcopipe_handle_resps)

                    cur_req_scopipelist =
                        UniteArrayLists(cur_req_scopipelist, curStageAnlz_info.scopipe_handle_reqs)
                    cur_resp_scopipelist =
                        UniteArrayLists(cur_resp_scopipelist, curStageAnlz_info.scopipe_handle_resps)

                } else {
                    next_resp_mcopipelist = UniteArrayLists(
                        next_resp_mcopipelist,
                        curStageAnlz_info.mcopipe_handle_resps
                    )
                    next_resp_scopipelist = UniteArrayLists(
                        next_resp_scopipelist,
                        curStageAnlz_info.scopipe_handle_resps
                    )
                }
            }

            curStageInfo.mcopipe_handles = UniteArrayLists(UniteArrayLists(cur_req_mcopipelist, cur_resp_mcopipelist), CrossArrayLists(prev_req_mcopipelist, next_resp_mcopipelist))
            curStageInfo.scopipe_handles = UniteArrayLists(UniteArrayLists(cur_req_scopipelist, cur_resp_scopipelist), CrossArrayLists(prev_req_scopipelist, next_resp_scopipelist))

            for (mcopipe_handle in curStageInfo.mcopipe_handles) {
                var mcopipe_handle_info = TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle] as __mcopipe_handle_info

                curStage.AddRdVar(mcopipe_handle_info.if_id)
                curStage.AddRdVar(mcopipe_handle_info.rdreq_pending)
                curStage.AddRdVar(mcopipe_handle_info.tid)
                curStage.AddRdVar(mcopipe_handle_info.resp_done)
                curStage.AddRdVar(mcopipe_handle_info.rdata)

                curStage.AddWrVar(mcopipe_handle_info.if_id)
                curStage.AddWrVar(mcopipe_handle_info.rdreq_pending)
                curStage.AddWrVar(mcopipe_handle_info.tid)
                curStage.AddWrVar(mcopipe_handle_info.resp_done)
                curStage.AddWrVar(mcopipe_handle_info.rdata)
            }

            for (scopipe_handle in curStageInfo.scopipe_handles) {
                var scopipe_handle_info = TranslateInfo.__scopipe_handle_assocs[scopipe_handle] as __scopipe_handle_info

                curStage.AddRdVar(scopipe_handle_info.if_id)
                curStage.AddRdVar(scopipe_handle_info.we)

                curStage.AddWrVar(scopipe_handle_info.if_id)
                curStage.AddWrVar(scopipe_handle_info.we)
            }

            // println("mcopipe analysis: " + StageList[CUR_STAGE_INDEX].name)
            // println("mcopipe_handles: " + StageAssocList[CUR_STAGE_INDEX].mcopipe_handles.size)
        }

        // Generate resources for locals
        for (CUR_STAGE_INDEX in 0 until TranslateInfo.StageList.size) {
            var curStage = TranslateInfo.StageList[CUR_STAGE_INDEX]
            var curStageInfo = TranslateInfo.StageInfoList[CUR_STAGE_INDEX]

            var prev_wr_pvarlist    = ArrayList<hw_var>()
            var cur_wr_pvarlist     = ArrayList<hw_var>()
            var cur_rd_pvarlist     = ArrayList<hw_var>()
            var next_rd_pvarlist    = ArrayList<hw_var>()

            for (STAGE_INDEX_ANLZ in 0 until TranslateInfo.StageList.size) {
                if (STAGE_INDEX_ANLZ < CUR_STAGE_INDEX) {
                    prev_wr_pvarlist = UniteArrayLists(prev_wr_pvarlist, CrossArrayLists(TranslateInfo.StageList[STAGE_INDEX_ANLZ].wrvars, locals))

                } else if (STAGE_INDEX_ANLZ == CUR_STAGE_INDEX) {
                    cur_wr_pvarlist = UniteArrayLists(cur_wr_pvarlist, CrossArrayLists(TranslateInfo.StageList[STAGE_INDEX_ANLZ].wrvars, locals))
                    cur_rd_pvarlist = UniteArrayLists(cur_rd_pvarlist, CrossArrayLists(TranslateInfo.StageList[STAGE_INDEX_ANLZ].rdvars, locals))

                } else {
                    next_rd_pvarlist = UniteArrayLists(next_rd_pvarlist, CrossArrayLists(TranslateInfo.StageList[STAGE_INDEX_ANLZ].rdvars, locals))

                }
            }

            var pContext_locals = UniteArrayLists(UniteArrayLists(cur_wr_pvarlist, cur_rd_pvarlist), CrossArrayLists(prev_wr_pvarlist, next_rd_pvarlist))
            var pContext_notnew = CrossArrayLists(UniteArrayLists(cur_rd_pvarlist, next_rd_pvarlist), prev_wr_pvarlist)

            // processing pContext list
            for (local in pContext_locals) {
                if (local is hw_local) {
                    var new_local = cyclix_gen.local(
                        (curStageInfo.name_prefix + local.name),
                        local.vartype,
                        local.defimm
                    )
                    curStageInfo.pContext_local_dict.put(local, new_local)
                } else if (local is hw_local_sticky) {
                    var new_local = cyclix_gen.global(
                        (curStageInfo.name_prefix + local.name),
                        local.vartype,
                        local.defimm
                    )
                    curStageInfo.pContext_local_dict.put(local, new_local)
                }
            }

            // generating global sources
            for (notnew in pContext_notnew) {
                if (notnew is hw_local) {
                    curStageInfo.pContext_srcglbls.add(notnew)
                }
            }
            for (accum_tgt in curStageInfo.accum_tgts) {
                if (!curStageInfo.pContext_srcglbls.contains(accum_tgt)) {
                    curStageInfo.pContext_srcglbls.add(accum_tgt)
                    curStageInfo.newaccums.add(accum_tgt)
                }
            }

            var stage_buf_struct = hw_struct(curStageInfo.name_prefix + "TRX_BUF_STRUCT")
            for (srcglbl in curStageInfo.pContext_srcglbls) {
                stage_buf_struct.add(srcglbl.name, srcglbl.vartype, srcglbl.defimm)
            }
            curStageInfo.TRX_BUF = cyclix_gen.global(curStageInfo.name_prefix + "TRX_BUF", stage_buf_struct, curStageInfo.TRX_BUF_SIZE-1, 0)
            curStageInfo.TRX_BUF_COUNTER = cyclix_gen.uglobal(curStageInfo.name_prefix + "TRX_BUF_COUNTER", GetWidthToContain(curStageInfo.TRX_BUF_SIZE)-1, 0, "0")

            for (pContext_local_dict_entry in curStageInfo.pContext_local_dict) {
                curStageInfo.var_dict.put(pContext_local_dict_entry.key, pContext_local_dict_entry.value)
            }
            for (global_assoc in TranslateInfo.__global_assocs) {
                curStageInfo.var_dict.put(global_assoc.key, global_assoc.value.cyclix_global)
            }
        }

        MSG(DEBUG_FLAG, "Generating logic")

        MSG(DEBUG_FLAG, "mcopipe processing")
        for (mcopipe_if in TranslateInfo.__mcopipe_if_assocs) {
            cyclix_gen.assign(mcopipe_if.value.wr_done, 0)
            cyclix_gen.assign(mcopipe_if.value.rd_done, 0)

            // forming mcopipe ptr next values
            cyclix_gen.add_gen(mcopipe_if.value.wr_ptr_next, mcopipe_if.value.wr_ptr, 1)
            cyclix_gen.add_gen(mcopipe_if.value.rd_ptr_next, mcopipe_if.value.rd_ptr, 1)
        }

        MSG(DEBUG_FLAG, "rdbuf logic")
        for (global in TranslateInfo.__global_assocs) {
            cyclix_gen.assign(global.value.cyclix_global_buf, global.value.cyclix_global)
        }

        MSG(DEBUG_FLAG, "logic of stages")
        for (CUR_STAGE_INDEX in TranslateInfo.StageList.lastIndex downTo 0) {

            var curStage        = TranslateInfo.StageList[CUR_STAGE_INDEX]
            var curStageInfo    = TranslateInfo.StageInfoList[CUR_STAGE_INDEX]

            MSG(DEBUG_FLAG, "[[ Stage processing: " + curStage.name + " ]]")

            // Asserting pctrl defaults (deployed even if signal is not used)
            cyclix_gen.assign(curStageInfo.pctrl_succ, 0)
            cyclix_gen.assign(curStageInfo.pctrl_working, 0)

            // Generating root pctrls
            MSG(DEBUG_FLAG, "#### Initializing pctrls ####")

            cyclix_gen.begif(curStageInfo.pctrl_stalled_glbl)
            run {
                cyclix_gen.assign(curStageInfo.pctrl_new, 0)

                // reattempt execution
                cyclix_gen.assign(curStageInfo.pctrl_stalled_glbl, 0)
                cyclix_gen.assign(curStageInfo.pctrl_active_glbl, cyclix_gen.lnot(curStageInfo.pctrl_killed_glbl))
            }; cyclix_gen.endif()
            cyclix_gen.begelse()
            run {
                if (CUR_STAGE_INDEX == 0) {
                    // new transaction
                    cyclix_gen.assign(curStageInfo.pctrl_active_glbl, 1)
                    cyclix_gen.assign(curStageInfo.pctrl_killed_glbl, 0)
                }
                cyclix_gen.lor_gen(curStageInfo.pctrl_new, curStageInfo.pctrl_active_glbl, curStageInfo.pctrl_killed_glbl)
            }; cyclix_gen.endif()

            cyclix_gen.assign(curStageInfo.pctrl_finish, 0)
            cyclix_gen.assign(curStageInfo.pctrl_flushreq, 0)
            cyclix_gen.assign(curStageInfo.pctrl_nevictable, 0)

            // Generating "occupied" pctrl
            cyclix_gen.bor_gen(curStageInfo.pctrl_occupied, curStageInfo.pctrl_active_glbl, curStageInfo.pctrl_killed_glbl)

            // rdy signal: before processing
            if (curStage.busy_mode == PSTAGE_BUSY_MODE.BUFFERED) {
                cyclix_gen.assign(curStageInfo.pctrl_rdy, !curStageInfo.pctrl_occupied)
            }

            // Forming mcopipe_handles_last list
            MSG(DEBUG_FLAG ,"Detecting last mcopipe handles")
            for (mcopipe_handle in curStageInfo.mcopipe_handles) {
                if (CUR_STAGE_INDEX == TranslateInfo.StageList.lastIndex) {
                    curStageInfo.mcopipe_handles_last.add(mcopipe_handle)
                } else {
                    if (!TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].mcopipe_handles_last.contains(mcopipe_handle)) {
                        curStageInfo.mcopipe_handles_last.add(mcopipe_handle)
                    }
                }
            }

            // pipeline flush processing
            if (CUR_STAGE_INDEX < TranslateInfo.StageList.lastIndex) {
                cyclix_gen.bor_gen(curStageInfo.pctrl_flushreq, curStageInfo.pctrl_flushreq, TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].pctrl_flushreq)
            }

            // Analyzing local stickies
            var local_stickies_new      = ArrayList<hw_local_sticky>()
            var local_stickies_notnew   = ArrayList<hw_local_sticky>()
            for (local in curStageInfo.pContext_local_dict) {
                if (local.key is hw_local_sticky) {
                    if (CUR_STAGE_INDEX == 0) {
                        local_stickies_new.add(local.key as hw_local_sticky)
                    } else if (TranslateInfo.StageInfoList[CUR_STAGE_INDEX-1].pContext_local_dict.containsKey(local.key)) {
                        local_stickies_notnew.add(local.key as hw_local_sticky)
                    } else {
                        local_stickies_new.add(local.key as hw_local_sticky)
                    }
                }
            }

            // not a bubble
            cyclix_gen.begif(curStageInfo.pctrl_occupied)
            run {

                MSG(DEBUG_FLAG, "#### Initializing new psticky descriptors ####")
                if (local_stickies_new.size != 0) {
                    cyclix_gen.begif(curStageInfo.pctrl_new)
                    run {
                        for (sticky_new in local_stickies_new) {
                            cyclix_gen.assign(curStageInfo.TranslateVar(sticky_new), sticky_new.defimm)
                        }
                    }; cyclix_gen.endif()
                }

                MSG(DEBUG_FLAG, "#### Initializing new newaccums descriptors ####")
                if (curStageInfo.newaccums.size != 0) {
                    cyclix_gen.begif(curStageInfo.pctrl_new)
                    run {
                        for (newaccum in curStageInfo.newaccums) {
                            var fracs = hw_fracs(0)
                            fracs.add(hw_frac_SubStruct(newaccum.name))
                            cyclix_gen.assign(curStageInfo.TRX_BUF, fracs, newaccum.defimm)
                        }
                    }; cyclix_gen.endif()
                }

                MSG(DEBUG_FLAG, "Fetching locals from src_glbls")
                for (src_glbl in curStageInfo.pContext_srcglbls) {
                    cyclix_gen.assign(curStageInfo.pContext_local_dict[src_glbl]!!, cyclix_gen.subStruct(curStageInfo.TRX_BUF[0], src_glbl.name))
                }

                MSG(DEBUG_FLAG, "#### Acquiring mcopipe rdata ####")
                for (mcopipe_handle in curStageInfo.mcopipe_handles) {

                    ///
                    var if_id_translated            = curStageInfo.TranslateVar(TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!.if_id)
                    var rdreq_pending_translated    = curStageInfo.TranslateVar(TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!.rdreq_pending)
                    var tid_translated              = curStageInfo.TranslateVar(TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!.tid)
                    var resp_done_translated        = curStageInfo.TranslateVar(TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!.resp_done)
                    var rdata_translated            = curStageInfo.TranslateVar(TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!.rdata)

                    cyclix_gen.begif(rdreq_pending_translated)
                    run {

                        var IF_NUM = 0
                        for (mcopipe_if in TranslateInfo.__mcopipe_handle_reqdict[mcopipe_handle]!!) {
                            var mcopipe_if_assoc = TranslateInfo.__mcopipe_if_assocs[mcopipe_if]!!

                            cyclix_gen.begif(cyclix_gen.eq2(if_id_translated, hw_imm(if_id_translated.vartype.dimensions, IF_NUM.toString())))
                            run {

                                cyclix_gen.begif(cyclix_gen.eq2(tid_translated, mcopipe_if_assoc.rd_ptr))
                                run {

                                    var fifo_rdata = cyclix_gen.local(GetGenName("mcopipe_rdata"), mcopipe_if.rdata_vartype, "0")

                                    cyclix_gen.begif(cyclix_gen.fifo_rd_unblk(mcopipe_if_assoc.resp_fifo, fifo_rdata))
                                    run {
                                        // acquiring data
                                        cyclix_gen.assign(rdreq_pending_translated, 0)
                                        cyclix_gen.assign(resp_done_translated, 1)
                                        cyclix_gen.assign(rdata_translated, fifo_rdata)

                                        // mcopipe rd done
                                        cyclix_gen.assign(mcopipe_if_assoc.rd_done, 1)
                                    }; cyclix_gen.endif()

                                }; cyclix_gen.endif()

                            }; cyclix_gen.endif()
                            IF_NUM++
                        }

                    }; cyclix_gen.endif()
                }

                // forming mcopipe rdreq inprogress attribute
                MSG(DEBUG_FLAG, "#### Forming mcopipe rdreq inprogress attribute ####")
                var mcopipe_rdreq_inprogress = cyclix_gen.ulocal(GetGenName(curStageInfo.name_prefix + "mcopipe_rdreq_inprogress"), 0, 0, "0")
                cyclix_gen.assign(mcopipe_rdreq_inprogress, 0)
                for (mcopipe_handle in curStageInfo.mcopipe_handles) {
                    cyclix_gen.lor_gen(mcopipe_rdreq_inprogress, mcopipe_rdreq_inprogress, curStageInfo.TranslateVar(TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!.rdreq_pending))
                }

                // do not start payload if flush requested
                MSG(DEBUG_FLAG, "#### Pipeline flush processing ####")
                cyclix_gen.begif(curStageInfo.pctrl_flushreq)
                run {
                    if (CUR_STAGE_INDEX == 0) {
                        cyclix_gen.begif(mcopipe_rdreq_inprogress)
                        run {
                            curStageInfo.pkill_cmd_internal(cyclix_gen)
                        }; cyclix_gen.endif()
                        cyclix_gen.begelse()
                        run {
                            // Dropping transaction context //
                            for (local in curStageInfo.pContext_local_dict) {
                                if (local.key is hw_local_sticky) cyclix_gen.assign(local.value, local.value.defimm)
                            }
                            for (srcglbl in curStageInfo.pContext_srcglbls) {
                                cyclix_gen.assign(curStageInfo.TranslateVar(srcglbl), srcglbl.defimm)
                                var fracs = hw_fracs(0)
                                fracs.add(hw_frac_SubStruct(srcglbl.name))
                                cyclix_gen.assign(curStageInfo.TRX_BUF, fracs, srcglbl.defimm)
                            }
                        }; cyclix_gen.endif()
                    } else {
                        curStageInfo.pkill_cmd_internal(cyclix_gen)
                    }
                }; cyclix_gen.endif()

            }; cyclix_gen.endif()
            cyclix_gen.begelse()
            run {
                for (mcopipe_handle in curStageInfo.mcopipe_handles) {
                    var mcopipe_handle_assoc = TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!
                    cyclix_gen.assign(curStageInfo.TranslateVar(mcopipe_handle_assoc.resp_done), 0)
                    cyclix_gen.assign(curStageInfo.TranslateVar(mcopipe_handle_assoc.rdreq_pending), 0)
                }
            }; cyclix_gen.endif()

            MSG(DEBUG_FLAG, "#### Saving succ targets ####")        // for indexed assignments
            for (assign_succ_assoc in curStageInfo.assign_succ_assocs) {
                cyclix_gen.assign(assign_succ_assoc.value.buf, curStageInfo.TranslateVar(assign_succ_assoc.key))
            }

            MSG(DEBUG_FLAG, "#### Generating payload expressions ####")
            for (expr in curStage.expressions) {
                reconstruct_expression(DEBUG_FLAG,
                    cyclix_gen,
                    expr,
                    pipex_import_expr_context(curStageInfo.var_dict, curStage, TranslateInfo, curStageInfo)
                )
            }

            // forming nevictable pctrl
            for (mcopipe_handle in curStageInfo.mcopipe_handles_last) {
                cyclix_gen.lor_gen(curStageInfo.pctrl_nevictable, curStageInfo.pctrl_nevictable, curStageInfo.TranslateVar(TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!.rdreq_pending))
            }

            MSG(DEBUG_FLAG, "#### Processing of next pstage busyness ####")
            if (TranslateInfo.pipeline.pipeline_fc_mode == PIPELINE_FC_MODE.CREDIT_BASED) {
                if (CUR_STAGE_INDEX == 0) {
                    cyclix_gen.begif(cyclix_gen.eq2(TranslateInfo.gencredit_counter, TranslateInfo.StageList.size-1))
                    run {
                        curStageInfo.pstall_ifactive_cmd(cyclix_gen)
                    }; cyclix_gen.endif()
                }
            } else {
                if (CUR_STAGE_INDEX < TranslateInfo.StageList.lastIndex) {
                    cyclix_gen.begif(!TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].pctrl_rdy)
                    run {
                        // prepeat from next pstage requested
                        curStageInfo.pstall_ifactive_cmd(cyclix_gen)
                        // protection from evicting broken transaction with unfinished I/O by subsequent transaction
                        cyclix_gen.begif(curStageInfo.pctrl_nevictable)
                        run {
                            curStageInfo.pstall_ifoccupied_cmd(cyclix_gen)
                        }; cyclix_gen.endif()
                    }; cyclix_gen.endif()
                }
            }

            // Forced stalling in case any last mcopipe pending reads are not satisfied
            MSG(DEBUG_FLAG, "#### Forced stalling in case any last mcopipe pending reads are not satisfied ####")
            for (mcopipe_handle in curStageInfo.mcopipe_handles_last) {
                // check if mcopipe rd request is pending
                cyclix_gen.begif(curStageInfo.TranslateVar(TranslateInfo.__mcopipe_handle_assocs[mcopipe_handle]!!.rdreq_pending))
                run {
                    // forced stalling
                    curStageInfo.pstall_ifoccupied_cmd(cyclix_gen)
                }; cyclix_gen.endif()
            }
            // TODO: forced stalling in case any last scopipe pending reads are not satisfied

            // pstage_finish and pstage_succ formation
            MSG(DEBUG_FLAG, "#### pctrl_finish and pctrl_succ formation ####")
            cyclix_gen.begif(curStageInfo.pctrl_stalled_glbl)
            run {
                cyclix_gen.assign(curStageInfo.pctrl_finish, 0)
                cyclix_gen.assign(curStageInfo.pctrl_succ, 0)
            }; cyclix_gen.endif()
            cyclix_gen.begelse()
            run {
                cyclix_gen.assign(curStageInfo.pctrl_finish, curStageInfo.pctrl_occupied)
                cyclix_gen.assign(curStageInfo.pctrl_succ, curStageInfo.pctrl_active_glbl)
            }; cyclix_gen.endif()

            // credit counter processing
            if (pipeline_fc_mode == PIPELINE_FC_MODE.CREDIT_BASED) {
                if (CUR_STAGE_INDEX == 0) {
                    cyclix_gen.begif(curStageInfo.pctrl_succ)
                    run {
                        cyclix_gen.add_gen(TranslateInfo.gencredit_counter, TranslateInfo.gencredit_counter, 1)
                    }; cyclix_gen.endif()
                } else if (CUR_STAGE_INDEX == TranslateInfo.StageList.lastIndex) {
                    cyclix_gen.begif(curStageInfo.pctrl_succ)
                    run {
                        cyclix_gen.sub_gen(TranslateInfo.gencredit_counter, TranslateInfo.gencredit_counter, 1)
                    }; cyclix_gen.endif()
                }
            }

            // asserting succ signals
            MSG(DEBUG_FLAG, "#### Asserting succ signals ####")
            if (curStageInfo.assign_succ_assocs.size > 0) {
                cyclix_gen.begif(curStageInfo.pctrl_succ)
                run {
                    for (assign_succ_assoc in curStageInfo.assign_succ_assocs) {
                        cyclix_gen.begif(assign_succ_assoc.value.req)
                        run {
                            cyclix_gen.assign(curStageInfo.TranslateVar(assign_succ_assoc.key), assign_succ_assoc.value.buf)
                        }; cyclix_gen.endif()
                    }
                }; cyclix_gen.endif()
            }

            // processing context in case transaction is finished
            cyclix_gen.begif(curStageInfo.pctrl_finish)
            run {
                // programming next stage in case transaction is able to propagate
                if (CUR_STAGE_INDEX < TranslateInfo.StageList.lastIndex) {
                    if (pipeline_fc_mode != PIPELINE_FC_MODE.CREDIT_BASED) cyclix_gen.begif(TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].pctrl_rdy)
                    run {
                        // propagating transaction context
                        for (local in TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].pContext_local_dict) {
                            if (curStageInfo.pContext_local_dict.containsKey(local.key)) {
                                // having data to propagate
                                if (local.key is hw_local) {
                                    // propagating locals
                                    if (TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].pContext_srcglbls.contains(local.key)) {
                                        var fracs = hw_fracs(TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].TRX_BUF_COUNTER)
                                        fracs.add(hw_frac_SubStruct(local.key.name))
                                        cyclix_gen.assign(TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].TRX_BUF, fracs, curStageInfo.TranslateVar(local.key))
                                    }
                                } else {
                                    // propagating stickies
                                    cyclix_gen.assign(TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].pContext_local_dict[local.key]!!, curStageInfo.TranslateVar(local.key))
                                }
                            }
                        }
                        cyclix_gen.add_gen(TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].TRX_BUF_COUNTER, TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].TRX_BUF_COUNTER, 1)

                        // propagating pctrls
                        cyclix_gen.assign(TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].pctrl_active_glbl, curStageInfo.pctrl_active_glbl)
                        cyclix_gen.assign(TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].pctrl_killed_glbl, curStageInfo.pctrl_killed_glbl)
                        cyclix_gen.assign(TranslateInfo.StageInfoList[CUR_STAGE_INDEX+1].pctrl_stalled_glbl, 0)
                    }; if (pipeline_fc_mode != PIPELINE_FC_MODE.CREDIT_BASED) cyclix_gen.endif()
                }

                // clearing itself
                cyclix_gen.assign(curStageInfo.pctrl_active_glbl, 0)
                cyclix_gen.assign(curStageInfo.pctrl_killed_glbl, 0)
                cyclix_gen.assign(curStageInfo.pctrl_stalled_glbl, 0)
                for (BUF_INDEX in 0 until curStageInfo.TRX_BUF_SIZE-1) {
                    cyclix_gen.assign(curStageInfo.TRX_BUF, hw_fracs(BUF_INDEX), curStageInfo.TRX_BUF[BUF_INDEX+1])
                }
                cyclix_gen.sub_gen(curStageInfo.TRX_BUF_COUNTER, curStageInfo.TRX_BUF_COUNTER, 1)

            }; cyclix_gen.endif()

            // rdy signal: after processing
            if (curStage.busy_mode == PSTAGE_BUSY_MODE.FALL_THROUGH) {
                cyclix_gen.assign(curStageInfo.pctrl_rdy, !curStageInfo.pctrl_stalled_glbl)
            }

            // working signal: succ or pstall
            cyclix_gen.bor_gen(curStageInfo.pctrl_working, curStageInfo.pctrl_succ, curStageInfo.pctrl_stalled_glbl)
        }

        for (__mcopipe_if_assoc in TranslateInfo.__mcopipe_if_assocs) {

            var mcopipe_if_assoc = __mcopipe_if_assoc.value

            // mcopipe rd fifo management
            cyclix_gen.begif(mcopipe_if_assoc.rd_done)
            run {
                cyclix_gen.assign(mcopipe_if_assoc.rd_ptr, mcopipe_if_assoc.rd_ptr_next)
                cyclix_gen.assign(mcopipe_if_assoc.full_flag, 0)

                cyclix_gen.begif(cyclix_gen.eq2(mcopipe_if_assoc.rd_ptr, mcopipe_if_assoc.wr_ptr))
                run {
                    cyclix_gen.assign(mcopipe_if_assoc.empty_flag, 1)
                }; cyclix_gen.endif()
            }; cyclix_gen.endif()

            // mcopipe wr fifo management
            cyclix_gen.begif(mcopipe_if_assoc.wr_done)
            run {
                cyclix_gen.assign(mcopipe_if_assoc.wr_ptr, mcopipe_if_assoc.wr_ptr_next)
                cyclix_gen.assign(mcopipe_if_assoc.empty_flag, 0)

                cyclix_gen.begif(cyclix_gen.eq2(mcopipe_if_assoc.rd_ptr, mcopipe_if_assoc.wr_ptr))
                run {
                    cyclix_gen.assign(mcopipe_if_assoc.full_flag, 1)
                }; cyclix_gen.endif()
            }; cyclix_gen.endif()
        }

        cyclix_gen.end()
        MSG(DEBUG_FLAG, "Translating to cyclix: complete")
        return cyclix_gen
    }
}
