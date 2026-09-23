param([string]$TvRoot = "C:\Dev\StreamDekTV")
$ErrorActionPreference = 'Stop'
$mobileRoot = Split-Path $PSScriptRoot
foreach ($name in @('MediaClassification.kt', 'MetadataLookupIdentity.kt', 'AddonMediaReference.kt')) {
  $mobile = [IO.File]::ReadAllText((Join-Path $mobileRoot "android/app/src/main/java/net/streamdek/mobile/nativeapp/$name"))
  $tv = [IO.File]::ReadAllText((Join-Path $TvRoot "android/app/src/main/java/com/streamdek/tv/nativeapp/data/$name"))
  $mobile = ($mobile -replace 'package net.streamdek.mobile.nativeapp', 'package shared').Replace("`r`n", "`n").Trim()
  $tv = ($tv -replace 'package com.streamdek.tv.nativeapp.data', 'package shared').Replace("`r`n", "`n").Trim()
  if ($mobile -cne $tv) { throw "Metadata contract drift: $name" }
}
Write-Output 'Mobile and TV metadata contracts match.'
