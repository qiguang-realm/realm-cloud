<template>
  <div class="app-container">
    <el-card class="box-card">
      <el-form :disabled="dialogStatus==='audit'" label-position="left" label-width="160px" :inline="true" :model="temp">

        <!--        <el-form ref="dataForm" :rules="rules" :model="temp" label-position="left" label-width="25%" :disabled="dialogStatus==='detail'" -->
        <!--                 style="width: 400px; margin-left:50px;">-->

        <el-form-item label="收费站名称:">
          <el-input v-model="temp.tollStationName" placeholder="收费站名称" style="width: 200px;"/>
        </el-form-item>
        <el-form-item label="当前定位:">
          <el-input v-model="temp.location" placeholder="当前定位" style="width: 200px;"/>
        </el-form-item>

        <el-form-item label="原收费站运营状态" prop="originalOperationalStatus">
          <el-select v-model="temp.originalOperationalStatus" placeholder="原收费站运营状态">
            <el-option label="正常" value="01"/>
            <el-option label="关闭" value="02"/>
            <el-option label="入口关闭、出口正常" value="03"/>
            <el-option label="入口正常、出口关闭" value="04"/>
            <el-option label="管制" value="05"/>
          </el-select>
        </el-form-item>

        <el-form-item label="提交运营状态" prop="submitOperationalStatus">
          <el-select v-model="temp.submitOperationalStatus" placeholder="提交运营状态">
            <el-option label="正常" value="01"/>
            <el-option label="关闭" value="02"/>
            <el-option label="入口关闭、出口正常" value="03"/>
            <el-option label="入口正常、出口关闭" value="04"/>
            <el-option label="管制" value="05"/>
          </el-select>
        </el-form-item>

        <!--        <el-form-item label="所需车型:">-->
        <!--          <el-input v-model="temp.vehicleType" placeholder="所需车型"/>-->
        <!--        </el-form-item>-->
        <!--        <el-form-item label="车辆数量:">-->
        <!--          <el-input v-model="temp.vehiclesNumber" placeholder="车辆数量"/>-->
        <!--        </el-form-item>-->

        <!--        <el-form-item label="联系人:">-->
        <!--          <el-input v-model="temp.liaisonMan" placeholder="联系人"/>-->
        <!--        </el-form-item>-->
        <!--        <el-form-item label="联系方式:">-->
        <!--          <el-input v-model="temp.contactInformation" placeholder="联系方式"/>-->
        <!--        </el-form-item>-->

        <!--        <el-row :gutter="20">-->
        <!--          <el-col :span="8">-->
        <!--            <el-form-item label="资质图片" prop="imageUrl">-->
        <!--              <el-upload-->
        <!--                action="#"-->
        <!--                list-type="picture-card"-->
        <!--                :auto-upload="true"-->
        <!--                :http-request="uploadFile"-->
        <!--                :limit="1"-->
        <!--                :on-exceed="handleExceed"-->
        <!--                :before-upload="beforeAvatarUpload"-->
        <!--                :before-remove="beforeRemove"-->
        <!--                :file-list="fileList"-->
        <!--              >-->
        <!--                <i slot="default" class="el-icon-plus"></i>-->
        <!--                <div slot="file" slot-scope="{file}">-->
        <!--                  <img class="el-upload-list__item-thumbnail" :src="file.url" alt="">-->
        <!--                  <span class="el-upload-list__item-actions">-->
        <!--                        <span class="el-upload-list__item-preview" @click="handlePictureCardPreview(file)">-->
        <!--                          <i class="el-icon-zoom-in"></i>-->
        <!--                        </span>-->
        <!--                        <span v-if="source!=='01'" class="el-upload-list__item-delete" @click="handleRemove(file)">-->
        <!--                          <i class="el-icon-delete"></i>-->
        <!--                        </span>-->
        <!--                      </span>-->
        <!--                </div>-->
        <!--              </el-upload>-->
        <!--              <el-dialog :visible.sync="dialogVisible">-->
        <!--                <img width="100%" :src="dialogImageUrl" alt="">-->
        <!--              </el-dialog>-->
        <!--            </el-form-item>-->

        <!--          </el-col>-->
        <!--        </el-row>-->
      </el-form>
    </el-card>

    <el-card class="box-card">
      <el-form label-position="left" label-width="80px" :inline="false" :model="temp">

        <el-form-item label="审核:">
          <el-radio-group v-model="temp.auditStatus">
            <el-radio label="02">同意</el-radio>
            <el-radio label="03">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="审核意见:" v-if="temp.auditStatus === '03'">
          <el-input v-model="temp.auditOpinions" placeholder="审核意见"/>
        </el-form-item>

        <el-form-item style="padding-bottom: 10px;">
          <el-button type="primary" @click="onSubmit">确定</el-button>
        </el-form-item>

      </el-form>
    </el-card>
  </div>
</template>

<script>

import {BASE_URL, fetchAudit, fetchInfo} from '@/api/roadCorrection'
import {uploadMerchantImage} from "@/api/merchant";

export default {
  name: 'Audit',
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
      BASE_URL,
      // 图片开始
      dialogImageUrl: '',
      dialogVisible: false,
      fileList: [],
      // 图片结束

      temp: {
        id: null,
        tollStationId: null,
        tollStationName: null,
        originalOperationalStatus: null,
        submitOperationalStatus: null,
        location: null,
        longitude: null,
        latitude: null,
        imageUrl: null,
        description: null,
        userId: null,
        userPhone: null,
        auditor: null,
        auditOpinions: null,
        auditStatus: null,
        auditTime: null,
        status: null,
        creator: null,
        createTime: null
      }
    }
  },

  created() {
    if (this.id !== null) {
      this.getInfo(this.id)
    }

  },
  methods: {
    getInfo(id) {
      fetchInfo({id: id}).then(res => {
        if (res.code === '0') {
          this.temp = res.clazz
          this.temp.auditStatus = '02'
          this.temp.auditOpinions = ''
          if (this.temp.imageUrl !== undefined && this.temp.imageUrl !== null && this.temp.imageUrl !== '') {
            this.fileList.push({
              url: res.clazz.readUrl
            });
          } else {
            this.fileList = [];
          }
        }
      })
    },
    /**上传图片开始**/
    //超出数量提醒
    handleExceed(files, fileList) {
      this.$message.warning(`上传文件个数已超限！`);
    },
    beforeRemove(file, fileList) {
      return true;
    },
    // 限制用户上传的图片格式和大小
    beforeAvatarUpload(file) {
      if (file.type == 'image/jpeg' || file.type == 'image/png' || file.type == 'image/jpg') {

      } else {
        this.$message.error('请上传 jpg,jpeg,png 格式图片');
        return false;
      }

      const isLtM = file.size / 1024 / 1024 < 20
      if (!isLtM) {
        this.$message.error('上传图片大小不能超过 20MB!')
        return false;
      }

      return true;
    },
    // 查看大图方法，默认无需修改
    handlePictureCardPreview(file) {
      this.dialogImageUrl = file.url;
      this.dialogVisible = true;
    },

    //删除
    handleRemove(file) {
      this.fileList = [];
      this.temp.imageUrl = "";
    },

    //上传
    uploadFile(headers) {
      var form = new FormData();
      form.append("file", headers.file);
      form.append("imageType", 0);
      form.append("moduleName", "收费站");
      uploadMerchantImage(form).then((data) => {
        if (data.success) {
          this.fileList.push({
            url: data.clazz.readUrl
          });
          this.temp.imageUrl = data.clazz.imageUrl
          this.$message.success('上传成功!');
        } else {
          this.$message.error('上传失败!');
        }
      }).catch((err) => {
        this.$message.error('上传异常!' + err);
      })
    },
    /**上传图片结束**/
    onSubmit() {
      fetchAudit(this.temp).then(res => {
        if (res.code === '0') {
          this.$message.success('审核成功')
          this.$emit('fatherMethod')
        }
      })
    }

  }
}
</script>

<style lang="scss" scoped>
.app-container {
  height: 310px;
  overflow-x: hidden;
  overflow-y: auto;

  .el-row {
    margin-bottom: 10px !important;
  }

  .my-col {
    display: flex;
    flex-direction: column;

    span {
      width: 80px;
      height: 25px;
      font-size: 15px;
      color: black;
    }
  }

  .el-form-item {
    margin-bottom: 10px;
  }

  .el-date-editor {
    width: 184px;
  }
}


.imageHeight {
  width: 25px !important;
  padding: 0 16px;
  display: flex;
  align-items: center;
  line-height: 26px;
  font-weight: 500;
}

.divColor {
  background: #e8f4ff;
  padding-top: 10px;
}
</style>
