<template>
  <div class="app-container">
    <el-form ref="dataForm" :rules="rules" :model="temp" label-position="left" label-width="25%"

             style="width: 400px; margin-left:50px;">

      <el-form-item label="省份:" prop="province">
        <el-select ref="province"
                   v-model="temp.province" placeholder="请选择省份" class="el-input" @change="handleFetchProvinces"
                   :disabled="dialogStatus==='update'">
          <el-option v-for="item in provinceList" :key="item.name" :label="item.name" :value="item.id"/>
        </el-select>
      </el-form-item>

      <el-form-item label="路段名称:" prop="roadName">
        <el-select ref="roadName"
                   v-model="temp.roadName" placeholder="请选择路段名称" class="el-input"
                   :disabled="dialogStatus==='update'">
          <el-option v-for="item in roadNameList" :key="item.name" :label="item.name" :value="item.id"/>
        </el-select>
      </el-form-item>

      <!--      <el-form-item label="路段名称" prop="roadName">-->
      <!--        <el-input v-model="temp.roadName" placeholder="路段名称：xx高速"/>-->
      <!--      </el-form-item>-->

      <!--      <el-form-item label="类型名称" prop="typeId">-->
      <!--        <el-select v-model="temp.typeId" placeholder="类型名称">-->
      <!--          <el-option label="交通事故" value="101"/>-->
      <!--          <el-option label="自然灾害类" value="102"/>-->
      <!--          <el-option label="临时养护施工" value="103"/>-->
      <!--          <el-option label="特殊车辆通行" value="104"/>-->
      <!--          <el-option label="道路损毁" value="105"/>-->
      <!--          <el-option label="设备故障" value="106"/>-->
      <!--          <el-option label="流量异常" value="107"/>-->
      <!--          <el-option label="公共卫生事件" value="108"/>-->
      <!--          <el-option label="社会安全事件" value="109"/>-->
      <!--          <el-option label="气象灾害预警" value="110"/>-->
      <!--          <el-option label="地质灾害预警" value="111"/>-->
      <!--          <el-option label="大中修施工养护" value="112"/>-->
      <!--          <el-option label="重大社会活动" value="113"/>-->
      <!--          <el-option label="其他预警事件" value="114"/>-->
      <!--          <el-option label="其他事件" value="115"/>-->

      <!--        </el-select>-->
      <!--      </el-form-item>-->


      <el-form-item label="事件描述" prop="eventDesc">
        <vue-quill-editor style="width: 900px;"
                          v-model="temp.eventDesc"
                          :eventDesc="temp.eventDesc"
                          :options="editorOption"
                          @change="onEditorChange($event)"
        />
      </el-form-item>

      <el-form-item label="发生时间" prop="happenTime">
        <el-date-picker
          v-model="temp.happenTime"
          style="width:100%!important;"
          type="datetime"
          value-format="yyyy-MM-dd HH:mm:ss"
          placeholder="发生时间"
        />
      </el-form-item>

    </el-form>

    <div v-if="dialogStatus!=='detail'" slot="footer" class="dialog-footer"
         style="text-align: center; margin-top: 20px;">
      <el-button type="primary" @click="onSubmit">确定</el-button>
    </div>

  </div>
</template>
<script>

import {fetchCreate, fetchInfo, fetchProvinces, fetchRoads, fetchUpdate} from '@/api/travelEvent'
import VueQuillEditor from '@/components/quillEditor/quill.vue'
import Amap from "@/views/map";

export default {
  name: 'CreateEdit',
  components: {Amap, VueQuillEditor},
  props: {
    id: {
      type: String,
      default: ''  // 字符串类型，默认''
    },
    dialogStatus: {
      type: String,
      default: ''  // 字符串类型，默认''
    },
  },
  data() {
    return {
      provinceList: [],
      roadNameList: [],

      temp: {
        id: null,
        eventId: '',
        typeId: '',
        typeName: '',
        province: '',
        roadId: '',
        roadName: '',
        eventDesc: '',
        happenTime: null
      },
      editorOption: {
        placeholder: '请在这里输入',
        theme: 'snow', // 主题 snow/bubble
        modules: {
          history: {
            delay: 1000,
            maxStack: 50,
            userOnly: false
          },
          toolbar: {
            handlers: {
              image: function (value) {
                if (value) {
                  // 调用element的图片上传组件
                  document.querySelector('.avatar-uploader input').click()
                } else {
                  this.quill.format('image', false)
                }
              }
            }
          }
        }
      },


      // 表单校验
      rules: {
        roadName: [
          {required: true, message: '请输入路段名称', trigger: 'blur'}
        ],
        province: [
          {required: true, message: '请输入省份', trigger: 'blur'}
        ],
        eventDesc: [
          {required: true, message: '请输入事件描述', trigger: 'blur'}
        ],
        happenTime: [
          {required: true, message: '请输入发生时间', trigger: 'blur'}
        ],


      }
    }
  },
  created() {
    if (this.id !== null) {
      this.getInfo(this.id)
    }
    this.getProvinces()
  },
  methods: {
    getInfo(id) {
      fetchInfo({id: id}).then(res => {
        if (res.code === '0') {
          this.temp = res.clazz
        }
      })
    },

    // 获取所有父节点
    getProvinces() {
      fetchProvinces({}).then(res => {
        if (res.code === '0') {
          this.provinceList = res.clazz
        }
      })
    },

    // 获取父节点所有子节点
    handleFetchProvinces() {
      // console.log("id", this.temp.province)
      fetchRoads({id: this.temp.province}).then(res => {
        if (res.code === '0') {
          this.roadNameList = res.clazz
          this.temp.roadName = null; // 重置子节点的选择
        }
      })
    },

    // 富文本
    onEditorChange({quill, html, text}) {
      console.log('editor change!', quill, html, text)
      this.form.eventDesc = html
    },

    //提交按钮
    onSubmit: function () {
      this.$refs["dataForm"].validate(valid => {
        if (valid) {
          // this.infos.id = this.id
          (this.id === null ? fetchCreate(this.temp) : fetchUpdate(this.temp)).then(res => {
            if (res.code === '0') {
              this.$emit('fatherMethod')
            }
          })
        }
      });
    },

  }
}
</script>

<style scoped lang="scss">
.app-container {
  padding: 5px !important;
  height: auto;

  .el-dialog__header {
    background-color: aliceblue;
    border-bottom: 1px solid #e6ebf5;
    padding-bottom: 15px !important;
  }

  .el-dialog__body {
    padding: 5px 0 0 5px !important;
  }

  ::v-deep.el-upload--picture-card {
    background-color: #fbfdff;
    border: 1px dashed #c0ccda;
    border-radius: 6px;
    -webkit-box-sizing: border-box;
    box-sizing: border-box;
    width: 50px;
    height: 50px;
    cursor: pointer;
    line-height: 58px !important;
    vertical-align: top;
  }

}

.el-dialog__body {
  padding: 5px 10px !important;
}

</style>
