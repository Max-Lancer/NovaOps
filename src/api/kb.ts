import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { KbChunkDto, KbDetailDto, KbDocumentDto, KbDocumentQueryDto, KbListItemDto, KbListQueryDto, KbUploadInitDto, KbUploadInitResultDto, KbVersionDto, SaveKbDto } from '@/types/kb'

export const getKbListApi = (params: KbListQueryDto) => {
  return request.get<PageResult<KbListItemDto>>('/kb', { params })
}

export const getKbDetailApi = (id: string) => {
  return request.get<KbDetailDto>(`/kb/${id}`)
}

export const saveKbApi = (payload: SaveKbDto) => {
  return request.post<KbDetailDto, SaveKbDto>('/kb/save', payload)
}

export const getKbVersionsApi = (id: string) => {
  return request.get<KbVersionDto[]>(`/kb/${id}/versions`)
}

export const getKbDocumentsApi = (params: KbDocumentQueryDto) => request.get<PageResult<KbDocumentDto>>('/kb/documents', { params })
export const uploadKbDocumentApi = (file: File, title?: string) => { const data=new FormData(); data.append('file',file); if(title) data.append('title',title); return request.post<KbDocumentDto,FormData>('/kb/documents',data,{ timeout:60000 }) }
export const getKbDocumentChunksApi = (id:string) => request.get<KbChunkDto[]>(`/kb/documents/${id}/chunks`)
export const updateKbDocumentTitleApi = (id:string,title:string) => request.put<void,{title:string}>(`/kb/documents/${id}`,{title})
export const deleteKbDocumentApi = (id:string) => request.delete<void>(`/kb/documents/${id}`)
export const retryKbDocumentApi = (id:string) => request.post<KbDocumentDto>(`/kb/documents/${id}/retry`)

// 分片上传三接口：init 判定秒传/返回续传进度，chunks 逐片上传，merge 校验摘要并建档
export const initKbUploadApi = (payload:KbUploadInitDto) => request.post<KbUploadInitResultDto,KbUploadInitDto>('/kb/uploads/init',payload)
export const uploadKbChunkApi = (sessionId:string,index:number,chunk:Blob,fileName:string) => { const data=new FormData();data.append('file',chunk,fileName);return request.post<void,FormData>(`/kb/uploads/${sessionId}/chunks?index=${index}`,data,{timeout:60000}) }
export const mergeKbUploadApi = (sessionId:string) => request.post<KbDocumentDto>(`/kb/uploads/${sessionId}/merge`,undefined,{timeout:120000})
export const replaceKbDocumentApi = (id:string,file:File,title?:string) => { const data=new FormData();data.append('file',file);if(title)data.append('title',title);return request.post<KbDocumentDto,FormData>(`/kb/documents/${id}/replace`,data,{timeout:60000}) }
