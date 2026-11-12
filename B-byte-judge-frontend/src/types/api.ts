export interface ApiEnvelope<T> {
  code: number
  data: T
  message: string
  requestId: string
  serverTime: string
}

export interface AuthIdentity {
  userId: string
  account: string
  role: 'admin' | 'user'
}

export interface CsrfToken {
  headerName: string
  token: string
}

export interface ProblemSummary {
  id: string
  problemNo: number
  title: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD' | null
  status: 'DRAFT' | 'DELETED'
  revision: number
  createdAt: string | null
  updatedAt: string | null
}

export interface ProblemPage {
  items: ProblemSummary[]
  total: number
  pageNum: number
  pageSize: number
}

export interface ArtifactManifest {
  artifactId: string
  kind: string
  caseOrder: number
  displayOrder: number
  fileName: string
  byteSize: number
  contentDigest: string
}

export interface ProblemDetail extends ProblemSummary {
  everPublished: boolean
  draftId: string
  statement: string | null
  inputDescription: string | null
  outputDescription: string | null
  tags: string[] | null
  timeLimitCpuMs: number | null
  timeLimitWallMs: number | null
  memoryLimitMiB: number | null
  artifacts: ArtifactManifest[]
}
