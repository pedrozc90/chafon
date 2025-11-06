#!/bin/bash
#-- Autor.....: Viniciusalopes (viniciusalopes.com.br)
#-- Ajustado..: Emerson Leonardo (leonardo_2707@protonmail.com)
#-- Data......: 06/08/2019
#-- Finalidade: Listar o IP e o NOME dos dispositivos conectados à rede
#-- Distros...: Debian 10 Buster
# 
# Dependencias 
# samba-common-bin (que possui o nmblookup)
# iproute2
# nmap

# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
#			 	Atualizado para utilizar ip ao inves de ifconfig
# ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

# Identificação da Distribuição
distro=`cat /etc/*-release | grep PRETTY_NAME | cut -d "\"" -f2`
echo -e "\n > Distribuição: $distro"

# Conexoes
    echo -e "\n > Pesquisando conexões ativas..."
    qtdConexoes=`ip a | grep BROADCAST -c`

    if [ "$qtdConexoes" -eq 0 ]; then
        echo -e "\n ** Nenhuma conexão ativa. ** \n"
        exit
    else
        if [ "$qtdConexoes" -eq 1 ]; then
            echo -e "   Uma conexão ativa."
        else
            echo -e "   $qtdConexoes conexões ativas."
        fi
    fi

# Interfaces
    echo -e " > Obtendo Nomes da(s) interface(s) de rede..."
    interfaces=( `ip a | grep BROADCAST -B 0 | cut -d ":" -f2 -s | sed 's/ //g'` )

# Inets
    echo -e " > Obtendo IP(s) da(s) interface(s) de rede..."
    inets=( `ip a | grep BROADCAST -A 2 | grep -o 'inet6\? [0-9a-f\.:]*' | cut -f 2 -d ' '` )

# Selecao das interfaces
    selecionada=0
    i=0
    n=0
    if [ "$qtdConexoes" != "1" ]; then
        echo -e "\n   Interfaces de rede conectadas:"
        echo -e "  ----------------------------------"
        for inet in ${inets[@]}
        do
            let n=$n+1
            opcoes=( ${opcoes[@]} "$n" )
            echo -e "     $n-${interfaces[$i]}\t IP:$inet"
            let i=$i+1
        done
        echo -e "  ----------------------------------"

        invalida=1
        texto="   Selecione uma interface de rede: "
        while [ $invalida -eq 1 ]; do
            echo -e -n "$texto"
            read selecionada
            texto="   Digite o NÚMERO da interface de rede: "
            for opc in ${opcoes[@]}
            do
                if [ "$selecionada" = "$opc" ]; then
                    invalida=0
                fi
            done
        done

        let selecionada=$selecionada-1
    fi
    interface=${interfaces[$selecionada]}
    inet=${inets[$selecionada]}


# Gateway Padrão
    lin=( `sudo netstat -r -n | grep -m1 "$interface"` )
    gateway=${lin[1]}
    echo -e -n "\n > Gateway Padrão da interface [$interface] = $gateway"

# Prefixo
    octetos=( ${gateway//'.'/' '} )
    prefixo="${octetos[0]}.${octetos[1]}.${octetos[2]}"
    echo -e "\n > Prefixo da rede = $prefixo"
# IPs conectados
    echo -e -n " > Pesquisando IPs conectados à rede..."
    ips=( `sudo nmap -sP -n -T5 --exclude "$gateway" "$prefixo.0-255" | grep "Nmap scan report for " | cut -d " " -f5` )

echo -e -n "\n > Obtendo nomes dos dispositivos conectados à rede..."
echo -e "\n -------------------------------------------------------------------"

echo -e " IP\t\t->\tDispositivo"
echo -e " -------------------------------------------------------------------"
i=0
for ip in ${ips[@]}
do
    echo -n -e " ${ips[$i]}\t->\t"

    nome="nenhum"

    for inet in ${inets[@]}
    do
        # Se o ip pesquisado for do próprio computador
        if [ $ip = $inet ]; then
            nome=`uname -n`
        fi
    done

    #Obtém o nome do Dispositivo do Ip
    #if [ $nome = "nenhum" ]; then
    #    nome=`sudo nmblookup -A "$ip" | grep -m1 "<00>" | cut -d " " -f1`
    #fi

    # Se o cliente não fornecer o nome do Computador,
    if [ "$nome" = "" ]; then
        # Nome do Fabricante da placa de rede do cliente
        nome=`sudo nmap -sP -n $ip | grep "MAC Address: " | cut -d "(" -f2 | sed 's/ /_/g' | sed 's/)//g'`
    fi
    echo -e $nome
    let i=$i+1
done

echo -e " -------------------------------------------------------------------"
