/**
 * 知识库分片上传编排：内容摘要 → init（秒传判定 + 取回续传进度）→ 并发上传缺失分片 → merge。
 *
 * 断点续传的原理：init 会返回服务端已完整接收的分片序号（已落库），这里只补传缺失的那些；
 * 相同内容的文件在服务端已就绪时会命中秒传，直接跳过全部传输。
 */
import { initKbUploadApi, mergeKbUploadApi, uploadKbChunkApi } from '@/api/kb'
import type { KbDocumentDto } from '@/types/kb'

/** 分片大小需落在服务端允许的 256KB ~ 8MB 区间内。 */
export const CHUNK_SIZE = 5 * 1024 * 1024
/** 并发上传的分片数：太小传得慢，太大容易触发服务端/网关限流。 */
const MAX_PARALLEL = 3

export type UploadPhase = 'hashing' | 'uploading' | 'merging'

export interface UploadProgress {
  phase: UploadPhase
  uploadedChunks: number
  totalChunks: number
  percent: number
}

export interface ResumableUploadResult {
  /** 秒传命中时为 null（服务端直接复用既有文档）。 */
  document: KbDocumentDto | null
  instant: boolean
  documentId?: string
}

/** 计算整文件 SHA-256（十六进制小写），用作秒传键与服务端合并校验依据。 */
export const sha256Hex = async (file: File): Promise<string> => {
  if (!globalThis.crypto?.subtle) {
    throw new Error('当前浏览器不支持 Web Crypto，无法计算文件摘要')
  }
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

export const uploadFileResumable = async (
  file: File,
  onProgress?: (progress: UploadProgress) => void,
): Promise<ResumableUploadResult> => {
  onProgress?.({ phase: 'hashing', uploadedChunks: 0, totalChunks: 0, percent: 0 })
  const contentHash = await sha256Hex(file)

  const init = await initKbUploadApi({ fileName: file.name, fileSize: file.size, contentHash, chunkSize: CHUNK_SIZE })
  if (init.instant) {
    return { document: null, instant: true, documentId: init.documentId }
  }

  const sessionId = init.sessionId
  if (!sessionId) {
    throw new Error('上传初始化失败：服务端未返回会话 id')
  }
  const totalChunks = Math.max(1, Math.ceil(file.size / CHUNK_SIZE))
  const uploaded = new Set<number>(init.uploaded ?? [])
  const report = () =>
    onProgress?.({
      phase: 'uploading',
      uploadedChunks: uploaded.size,
      totalChunks,
      percent: Math.round((uploaded.size / totalChunks) * 100),
    })
  report()

  const missing: number[] = []
  for (let index = 0; index < totalChunks; index += 1) {
    if (!uploaded.has(index)) missing.push(index)
  }

  // 共享队列：shift 是同步的，单线程事件循环下多个 worker 不会抢到同一片
  const queue = [...missing]
  const worker = async () => {
    let index = queue.shift()
    while (index !== undefined) {
      const start = index * CHUNK_SIZE
      const chunk = file.slice(start, Math.min(file.size, start + CHUNK_SIZE))
      await uploadKbChunkApi(sessionId, index, chunk, file.name)
      uploaded.add(index)
      report()
      index = queue.shift()
    }
  }
  await Promise.all(Array.from({ length: Math.min(MAX_PARALLEL, missing.length) }, worker))

  onProgress?.({ phase: 'merging', uploadedChunks: totalChunks, totalChunks, percent: 100 })
  const document = await mergeKbUploadApi(sessionId)
  return { document, instant: false }
}
