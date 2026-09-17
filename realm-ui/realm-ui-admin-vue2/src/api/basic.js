import request from '@/utils/request'

import baseUrl from '@/api/baseUrl'

const BASE_URL = baseUrl.userBaseUrl

export function fetchFile(params) {
  const formData = new FormData()
  formData.append('file', params.file)
  return request({
    url: BASE_URL + '/lane/importCsv',
    method: 'post',
    config: {
      headers: { 'Content-Type': 'multipart/form-data' },
      responseType: 'blob'
    },
    data: formData
  })
}

export function fetchList(data) {
  return request({
    url: BASE_URL + '/lane/listPageLane',
    method: 'post',
    data: data
  })
}
