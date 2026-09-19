export interface KbListItemDto {
  id: string
  title: string
  tags: string[]
  author: string
  updatedAt: string
}

export interface KbVersionDto {
  id: string
  title: string
  tags: string[]
  content: string
  editor: string
  createdAt: string
}

export interface KbDetailDto extends KbListItemDto {
  content: string
}

export interface KbListQueryDto {
  page?: number
  pageSize?: number
  keyword?: string
  tag?: string
}

export interface SaveKbDto {
  id?: string
  title: string
  tags: string[]
  content: string
}

export type KbDocumentStatus = 'PARSING' | 'VECTORIZING' | 'READY' | 'FAILED'
export interface KbDocumentDto { id:string; title:string; fileName:string; fileType:'md'|'pdf'|'doc'|'docx'; fileSize:number; status:KbDocumentStatus; chunkCount:number; errorMsg?:string; contentHash?:string; createdBy:string; createdAt:string; updatedAt:string }
export interface KbChunkDto { id:string; documentId:string; chunkIndex:number; content:string; vectorId:string }
export interface KbDocumentQueryDto { page:number; pageSize:number; keyword?:string; fileType?:string; status?:KbDocumentStatus }
export interface KbUploadInitDto { fileName:string; fileSize:number; contentHash:string; chunkSize?:number }
export interface KbUploadInitResultDto { instant:boolean; sessionId?:string; documentId?:string; uploaded?:number[] }
