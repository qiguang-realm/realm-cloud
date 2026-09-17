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
      v-loading="loading"
      element-loading-text="拼命加载中"
      element-loading-spinner="el-icon-loading"
    >

      <el-form-item label="景区名称" prop="scenicSpotName">
        <el-input v-model="temp.scenicSpotName" placeholder="景区名称"/>
      </el-form-item>

      <el-form-item label="景区地址:" prop="scenicSpotAddress">
        <el-input v-model="temp.scenicSpotAddress" class="elInput" placeholder="请选择地址" @click="this.selectReMap">
          <i slot="suffix" class="el-icon-map-location el-input__icon" style="color:#67C23A" @click="this.selectReMap"/>
        </el-input>
      </el-form-item>

      <el-form-item label="兑换比例" prop="pointsRatio">
        <el-input v-model="temp.pointsRatio" oninput="value=value.replace(/^\.+|[^\d.]/g,'')" placeholder="兑换比例：10"/>
      </el-form-item>

      <el-form-item label="备注:" prop="remarks">
        <el-input v-model="temp.remarks" type="textarea" placeholder="备注"/>
      </el-form-item>

      <!--      <el-form-item label="所属活动:" prop="promotionId">-->
      <!--        <el-select ref="selectOptionActive" v-model="temp.promotionId" clearable placeholder="所属活动" class="el-input">-->
      <!--          <el-option v-for="item in activeList" :key="item.id" :label="item.promotionTheme" :value="item.id"/>-->
      <!--        </el-select>-->
      <!--      </el-form-item>-->

      <!--      <el-select v-model="value" clearable placeholder="请选择">-->
      <!--        <el-option-->
      <!--          v-for="item in options"-->
      <!--          :key="item.value"-->
      <!--          :label="item.label"-->
      <!--          :value="item.value">-->
      <!--        </el-option>-->
      <!--      </el-select>-->

      <el-form-item label="景区经度:" prop="longitude">
        <el-input v-model="temp.longitude" placeholder="景区经度"/>
      </el-form-item>
      <el-form-item label="景区纬度:" prop="latitude">
        <el-input v-model="temp.latitude" placeholder="景区纬度"/>
      </el-form-item>

      <el-form-item label="景区类型" prop="scenicType">
        <el-select v-model="temp.scenicType" placeholder="景区类型">
          <el-option label="图鉴景区" value="01"/>
          <el-option label="附近景区" value="02"/>
        </el-select>
      </el-form-item>

      <el-form-item label="备注:" :prop="temp.scenicType ==='01'?'remark': '' ">
        <el-input v-model="temp.remark" type="textarea" placeholder="备注" maxlength="200" show-word-limit/>
      </el-form-item>


      <el-form-item label="电话号码:" prop="phone">
        <el-input v-model="temp.phone" placeholder="电话号码" @input="(v)=>(temp.phone=v.replace(/[^\d]/g,''))"/>
      </el-form-item>

      <el-form-item v-show="temp.scenicType ==='02' " label="显示顺序" prop="scenicSort">
        <el-input-number v-model="temp.scenicSort" :min="1" :max="100000000000"/>
      </el-form-item>

      <el-form-item label="发布日期:" prop="releaseDate">
        <el-date-picker v-model="temp.releaseDate" placeholder="发布日期" value-format="yyyy-MM-dd" class="el-input"/>
      </el-form-item>

      <el-form-item label="生成CDK数量" :prop="dialogStatus ==='create'?'number': ''" v-if="dialogStatus ==='create'">
        <el-input-number v-model="temp.number" :min="1" :max="50" placeholder="生成CDK数量"/>
      </el-form-item>

      <el-form-item label="CDK有效期开始时间:" prop="validStartTime">
        <el-date-picker v-model="temp.validStartTime" placeholder="CDK有效期开始时间" type="datetime"
                        value-format="yyyy-MM-dd HH:mm:ss" class="el-input"/>
      </el-form-item>

      <el-form-item label="CDK有效期结束时间:" prop="validEndTime">
        <el-date-picker v-model="temp.validEndTime" placeholder="CDK有效期结束时间" type="datetime"
                        value-format="yyyy-MM-dd HH:mm:ss" class="el-input"/>
      </el-form-item>

      <el-form-item label="规则类型" prop="type">
        <el-select v-model="temp.type" placeholder="规则类型" :disabled="dialogStatus==='update'">
          <el-option label="第一届里程榜" value="01"/>
          <el-option label="第二届里程榜总榜" value="02"/>
          <el-option label="第二届里程榜周榜" value="03"/>
          <el-option label="第一届用户活跃榜" value="04"/>
          <el-option label="第二届用户活跃榜总榜" value="05"/>
          <el-option label="第二届用户活跃榜周榜" value="06"/>
          <el-option label="第一届邀约榜" value="07"/>
          <el-option label="第二届邀约榜总榜" value="08"/>
          <el-option label="第二届邀约榜周榜" value="09"/>
          <el-option label="第一届企业车辆榜" value="10"/>
          <el-option label="第二届企业车辆榜总榜" value="11"/>
          <el-option label="第二届企业车辆榜周榜" value="12"/>
          <el-option label="第一届邀请好友" value="13"/>
          <el-option label="第二届邀请好友" value="14"/>
          <el-option label="第一届普通邀请" value="15"/>
          <el-option label="第一届打卡有礼" value="16"/>
          <el-option label="第二届打卡有礼" value="17"/>
          <el-option label="第三届车主节积分夺宝" value="18"/>
          <el-option label="第三届货车节里程榜总榜" value="19"/>
          <el-option label="第三届货车节里程榜周榜" value="20"/>
          <el-option label="第三届货车节用户活跃榜总榜" value="21"/>
          <el-option label="第三届货车节用户活跃榜周榜" value="22"/>
          <el-option label="第三届货车节邀约榜总榜" value="23"/>
          <el-option label="第三届货车节邀约榜周榜" value="24"/>
          <el-option label="第三届货车节企业车辆榜总榜" value="25"/>
          <el-option label="第三届货车节企业车辆榜周榜" value="26"/>
          <el-option label="第三届货车节邀请有礼" value="27"/>
          <el-option label="第三届货车节打卡有礼" value="28"/>
          <el-option label="第三届货车节预约通行有礼" value="29"/>
          <el-option label="第三届1则" value="30"/>
          <el-option label="第三届2则" value="31"/>
          <el-option label="第三届3则" value="32"/>
          <el-option label="第三届4则" value="33"/>
          <el-option label="第三届5则" value="34"/>
          <el-option label="第三届6则" value="35"/>
          <el-option label="第三届7则" value="36"/>
          <el-option label="第三届8则" value="37"/>
          <el-option label="第三届9则" value="38"/>
          <el-option label="ETC预约有礼" value="39"/>
          <el-option label="第三届车主节打卡有礼" value="40"/>
        </el-select>
      </el-form-item>

      <el-form-item label="规则内容" prop="content">
        <vue-quill-editor style="width: 900px;"
                          v-model="temp.content"
                          :content="temp.content"
                          :options="editorOption"
                          @change="onEditorChange($event)"
        />
      </el-form-item>

      <template>
        <el-select
          v-model="value"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="请选择活动来源"
        >
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </template>


      <el-form-item label="Remark">
        <el-input
          v-model="temp.remark"
          :autosize="{ minRows: 2, maxRows: 4 }"
          type="textarea"
          placeholder="Please input"
        />
      </el-form-item>

      <el-form-item label="景区图片" prop="scenicSpotImg">
        <el-upload
          action="#"
          list-type="picture-card"
          :auto-upload="true"
          :http-request="uploadFile"
          :limit="1"
          :on-exceed="handleExceed"
          :before-upload="beforeAvatarUpload"
          :before-remove="beforeRemove"
          :file-list="fileList"
        >
          <i slot="default" class="el-icon-plus"/>
          <div slot="file" slot-scope="{file}">
            <img class="el-upload-list__item-thumbnail" :src="file.url" alt="">
            <span class="el-upload-list__item-actions">
              <span class="el-upload-list__item-preview" @click="handlePictureCardPreview(file)">
                <i class="el-icon-zoom-in"/>
              </span>
              <span v-if="dialogStatus!=='detail'" class="el-upload-list__item-delete" @click="handleRemove(file)">
                <i class="el-icon-delete"/>
              </span>
            </span>
          </div>
        </el-upload>
        <el-dialog :visible.sync="dialogVisible" append-to-body>
          <img width="100%" :src="dialogImageUrl" alt="">
        </el-dialog>
      </el-form-item>
    </el-form>

    <div
      v-if="dialogStatus!=='detail'"
      slot="footer"
      class="dialog-footer"
      style="text-align: center; margin-top: 20px;"
    >
      <el-button type="primary" @click="onSubmit">确定</el-button>
      <el-button @click="resetForm('dataForm')">重置</el-button>
    </div>

    <!-- 高德地图-->
    <el-dialog :visible.sync="dialogMapVisible" width="800px" append-to-body>
      <amap ref="amapRef" @on-marker-address-load="handleDisplay($event)"/>
    </el-dialog>
    <!-- 高德地图-->
  </div>
</template>
<script>

import {createScenicSpot, fetchScenicSpot, updateScenicSpot} from '@/api/scenicSpot'
import {uploadMerchantImage} from '@/api/merchant'
import VueQuillEditor from '@/components/quillEditor/vueQuillEditor.vue'
import Amap from '@/views/map'

export default {
  name: 'Create-Edit',
  components: {Amap, VueQuillEditor},
  props: {
    id: {
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
      loading: true,
      temp: {
        // id: undefined,
        id: null,
        scenicSpotName: '',
        scenicSpotImg: '',
        scenicSpotAddress: '',
        longitude: '',
        latitude: '',
        illustratedHandbook: '',
        status: '',
        createTime: null

        // promotionId: null,
        // promotionName: null
      },
      // activeList: [],
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
        scenicSpotName: [
          {required: true, message: '请输入景区名称', trigger: 'blur'}
        ],
        scenicSpotAddress: [
          {required: true, message: '请输入景区地址', trigger: 'blur'}
        ],
        longitude: [
          {required: true, message: '请输入景区经度', trigger: 'blur'}
        ],
        latitude: [
          {required: true, message: '请输入景区纬度', trigger: 'blur'}
        ]

        // mileage: [
        //   {required: true, message: "请输入里程数", trigger: "change"},
        //   {
        //     required: true,
        //     pattern: /^(?:0|[1-9][0-9]?|100)(.[0-9]{0,2})?$/,
        //     message: "请输入正确里程数，小数点后面最多两位",
        //     trigger: "change"
        //   }
        // ],
        // vehiclesNum: [
        //   {required: true, message: "车辆数不能为空", trigger: "blur"},
        //   {
        //     pattern: /^[0-9]*[1-9][0-9]*$/,
        //     message: "车辆数必须为正整数"
        //   }
        // ],

      }
    }
  },
  created() {
    // this.getActiveList()
    if (this.id !== null) {
      this.getInfo(this.id)
    }

  },
  methods: {
    getInfo(id) {
      fetchScenicSpot({id: id}).then(res => {
        if (res.code === '0') {
          this.temp = res.clazz

          let str = this.infos.activityDataSource;
          if (typeof str !== 'undefined' && str != null && str !== '') {
            this.infos.activityDataSource = str.split(',');
          } else {
            this.infos.activityDataSource = null;
          }

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

    // 获取当前活动列表
    // getActiveList() {
    //   promotionPageList({ pageCurrent: 1, pageSize: 10 , status: 1 }).then(res => {
    //     if (res.code === '0') {
    //       this.activeList = res.page.records
    //     }
    //   })
    // },
    // 高德地图---开始
    selectReMap() {
      this.isSetRecAddress = true
      this.dialogMapVisible = true
      this.$nextTick(() => {
        this.$refs.amapRef.resetData()
      })
    },
    handleDisplay({address, lat, lng, name, addressComponent}) {
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


    // 富文本
    onEditorChange({quill, html, text}) {
      console.log('editor change!', quill, html, text)
      this.form.content = html
    },

    // 提交按钮
    onSubmit: function () {

      this.$refs['dataForm'].validate(valid => {
        if (valid) {
          // this.infos.id = this.id

          let arr = this.infos.activityDataSource;
          if (arr !== undefined && arr != null && arr.length > 0) {
            this.infos.activityDataSource = arr.join(",");
          } else {
            this.infos.activityDataSource = null;
          }

          this.loading = true;

          (this.id === null ? createScenicSpot(this.temp) : updateScenicSpot(this.temp)).then(res => {

            // setTimeout(() => {
            //   this.listLoading = false
            // }, 200)

            if (res.code === '0') {
              this.loading = false
              this.$emit('fatherMethod')
            }
          })
        }
      })
    },

    // 表单重置
    resetForm(formName) {
      this.$refs['dataForm'].resetFields()
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
