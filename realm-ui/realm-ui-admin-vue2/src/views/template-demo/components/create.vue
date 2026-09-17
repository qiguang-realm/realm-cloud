<template>
  <div class="app-container">
    <!--      <el-form ref="dataForm" :rules="rules" :model="temp" label-position="left" label-width="70px" style="width: 400px; margin-left:50px;">-->
    <el-form
      ref="dataForm"
      :rules="rules"
      :model="temp"
      label-position="left"
      label-width="25%"
      :disabled="dialogStatus==='detail'"
      style="width: 400px; margin-left:50px;"
    >

      <el-form-item label="车牌号码" prop="licensePlateNumber">
        <el-input v-model="temp.licensePlateNumber" placeholder="车牌号码" @blur="getVehicleInfo" />
      </el-form-item>

      <el-form-item label="外省TC发行" prop="etcFlag" >
        <el-select v-model="temp.etcFlag" placeholder="是否外省ETC发行" disabled="isDisable">
          <el-option label="否" value="00" />
          <el-option label="是" value="01" />
        </el-select>
      </el-form-item>

      <!--     车牌颜色： 0:蓝色 1: 黄色 2:黑色 3:白色 4: 渐变绿 5: 黄绿双拼 6:蓝白渐变色       -->

      <el-form-item label="车牌颜色" prop="licensePlateColor">
        <el-select v-model="temp.licensePlateColor" placeholder="车牌颜色">
          <el-option label="蓝色" value="0" />
          <el-option label="黄色" value="1" />
          <el-option label="黑色" value="2" />
          <el-option label="白色" value="3" />
          <el-option label="渐变绿" value="4" />
          <el-option label="黄绿双拼" value="5" />
          <el-option label="蓝白渐变色" value="6" />
        </el-select>
      </el-form-item>

      <el-form-item label="车主姓名" prop="vehicleOwnerName" v-if="temp.etcFlag ==='00'">
        <el-input v-model="temp.vehicleOwnerName" placeholder="车主姓名" />
      </el-form-item>

      <el-form-item label="预留手机号" prop="vehicleOwnerPhone" v-if="temp.etcFlag ==='00'">
        <el-input disabled="isDisable" v-model="temp.vehicleOwnerPhone" placeholder="预留手机号" />
        <el-button style="margin-top:10px;" type="primary" @click="handleSend">
          {{count==60?'发送':count}}
        </el-button>
      </el-form-item>

      <el-form-item label="短信验证码" prop="verifyCode" v-if="temp.etcFlag ==='00'">
        <el-input v-model="temp.verifyCode" placeholder="短信验证码" />
      </el-form-item>

      <el-form-item label="OBU编号" prop="obuNumber">
        <el-input v-model="temp.obuNumber" placeholder="OBU编号" />
      </el-form-item>

    </el-form>

    <div
      v-if="dialogStatus!=='detail'"
      slot="footer"
      class="dialog-footer"
      style="text-align: center; margin-top: 20px;"
    >
      <el-button type="primary" @click="onSubmit">确定</el-button>
    </div>

    <!-- 高德地图-->
    <el-dialog :visible.sync="dialogMapVisible" width="800px" append-to-body>
      <amap ref="amapRef" @on-marker-address-load="handleDisplay($event)" />
    </el-dialog>
    <!-- 高德地图-->
  </div>
</template>
<script>

import { fetchBind, fetchScenicSpot, updateScenicSpot, getEtcUserByOwnerPhone, fetchSendVerCode } from '@/api/vehicle'
import { uploadMerchantImage } from '@/api/merchant'
import Amap from '@/views/map'

export default {
  name: 'Create',
  components: { Amap },
  props: {
    id: {
      type: String,
      default: '' // 字符串类型，默认''
    },
    userId: {
      type: String,
      default: '' // 字符串类型，默认''
    },
    dialogStatus: {
      type: String,
      default: '' // 字符串类型，默认''
    }
  },
  data() {
    return {
      temp: {
        // id: undefined,
        id: null,
        licensePlateNumber: '',
        userId: '',
        licensePlateColor: '',
        etcFlag: '',
        vehicleOwnerName: '',
        vehicleOwnerPhone: '',
        obuNumber: '',
        verifyCode: '',
        channel: '',
        creator: null

      },
      isDisable: false,
      count: 60,
      // 高德地图 弹窗
      isSetRecAddress: true,
      dialogMapVisible: false,

      // 长传图片 开始
      dialogImageUrl: '',
      dialogVisible: false,
      fileList: [],
      // 长传图片 结束

      // 表单校验
      rules: {
        licensePlateNumber: [
          { required: true, message: '请输入车牌号码', trigger: 'blur' }
        ],
        licensePlateColor: [
          { required: true, message: '请输入车牌颜色', trigger: 'blur' }
        ],
        vehicleOwnerName: [
          { required: true, message: '请输入车主姓名', trigger: 'blur' }
        ],
        obuNumber: [
          { required: true, message: '请输入OBU编号', trigger: 'blur' }
        ],

        etcFlag: [
          { required: true, message: '请输入ETC发行', trigger: 'blur' }
        ],

        vehicleOwnerPhone: [
          { required: true, message: '请输入预留手机号', trigger: 'blur' }
        ],

        verifyCode: [
          { required: true, message: '请输入短信验证码', trigger: 'blur' }
        ],

      }
    }
  },
  created() {
    if (this.id !== null) {
      this.getInfo(this.id)
    }
  },
  methods: {
    getVehicleInfo(){
      // var temp.
      var that = this
      var temp = this.temp
      that.isDisable =  false
      let licensePlateNumber =  this.temp.licensePlateNumber
      if(!licensePlateNumber) return
      getEtcUserByOwnerPhone({
        ownerPhone:null,
        vehicleNumber:licensePlateNumber
      }).then((res) => {
        if (res.code === '0') {
          temp.etcFlag = res.clazz.length>0?'00':'01'
          that.isDisable = false
          if(res.clazz.length>0){
            temp.vehicleOwnerPhone = res.clazz[0].ownerTelephone
            that.isDisable = true
          }
          that.temp = temp
        }else{
          this.$message.error('查询失败!' + err)
        }
      }).catch((err) => {
        this.$message.error('查询失败!' + err)
      })
    },

    handleSend(){
      let vehicleOwnerPhone =  this.temp.vehicleOwnerPhone
      if(!vehicleOwnerPhone || this.count<60) return
      var that = this
      fetchSendVerCode({
        mobile:vehicleOwnerPhone
      }).then((res) => {
        if (res.code === '0') {
          that.timerSetInterVal()
          that.$message.success('操作成功')
        }
      }).catch((err) => {
        this.$message.error('查询失败!' + err)
      })
    },

    timerSetInterVal(){
      var that = this
      var count  = this.count
      let timer = setInterval(function (){
        --count;
        that.count = count
        if(count==0){
          that.count =60
          clearInterval(timer)
        }
      },1000)
    },


    getInfo(id) {
      fetchScenicSpot({ id: id }).then(res => {
        if (res.code === '0') {
          this.temp = res.clazz
          // 景区图片URL地址
          if (this.temp.scenicSpotImg !== undefined && this.temp.scenicSpotImg !== null && this.temp.scenicSpotImg !== '') {
            this.fileList.push({
              url: res.clazz.readUrl
            })
          } else {
            this.fileList = []
          }
        }
      })
    },
    // 高德地图---开始
    selectReMap() {
      this.isSetRecAddress = true
      this.dialogMapVisible = true
      this.$nextTick(() => {
        this.$refs.amapRef.resetData()
      })
    },
    handleDisplay({ address, lat, lng, name, addressComponent }) {
      this.temp.scenicSpotAddress = address
      // this.temp.cityName = addressComponent.city;
      // this.temp.areaName = addressComponent.district;
      this.temp.longitude = String(lng)
      this.temp.latitude = String(lat)
      this.dialogMapVisible = false
    },
    // 高德地图---结束

    /** 上传景区图片---开始**/
    // 超出数量提醒
    handleExceed(files, fileList) {
      this.$message.warning(`上传文件个数已超限！`)
    },
    beforeRemove(file, fileList) {
      return true
    },
    // 限制用户上传的图片格式和大小
    beforeAvatarUpload(file) {
      if (file.type == 'image/jpeg' || file.type == 'image/png' || file.type == 'image/jpg') {
      } else {
        this.$message.error('请上传 jpg,jpeg,png 格式图片')
        return false
      }
      const isLtM = file.size / 1024 / 1024 < 20
      if (!isLtM) {
        this.$message.error('上传图片大小不能超过 20MB!')
        return false
      }
      return true
    },
    // 查看大图方法，默认无需修改
    handlePictureCardPreview(file) {
      this.dialogImageUrl = file.url
      this.dialogVisible = true
    },
    // 删除
    handleRemove(file) {
      this.fileList = []
      this.temp.scenicSpotImg = ''
    },
    // 上传
    uploadFile(headers) {
      var form = new FormData()
      form.append('file', headers.file)
      form.append('imageType', 0)
      form.append('moduleName', '景区图片')
      uploadMerchantImage(form).then((data) => {
        if (data.success) {
          this.fileList.push({
            url: data.clazz.readUrl
          })
          this.temp.scenicSpotImg = data.clazz.imageUrl
          this.$message.success('上传成功!')
        } else {
          this.$message.error('上传失败!')
        }
      }).catch((err) => {
        this.$message.error('上传异常!' + err)
      })
    },
    /** 上传景区图片---结束**/

    // 提交按钮
    onSubmit: function() {
      this.$refs['dataForm'].validate(valid => {
        if (valid) {
          // this.temp.id = this.id

          if (this.id === null) {
            this.temp.channel = '00'
            this.temp.userId = this.userId
          }
          (this.id === null ? fetchBind(this.temp) : updateScenicSpot(this.temp)).then(res => {
            if (res.code === '0') {
              this.$message.success('操作成功')
              this.$emit('fatherMethod')
            }
          })
        }
      })
    }

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
