#!/usr/bin/env python3.4

from plib.networks import NetworkConfig, Network, HostGroup, QualifiedIPAddress, NetworkInterface, Host


class IBRNetworkConfig(NetworkConfig):
    def __init__(self):
        super().__init__()

        pubnet = Network( 'public', '134.169.35.0/24' )
        self.add_network( pubnet )
        dynnet = Network( 'dynnet', '10.1.0.0/16' )
        self.add_network( dynnet )

        pr0net = Network( 'private0', '10.10.10.0/26' )
        self.add_network( pr0net )
        pr1net = Network( 'private1', '10.10.10.192/26' )
        self.add_network( pr1net )
        pr2net = Network( 'private2', '10.10.10.64/26' )
        self.add_network( pr2net )

        rdmanet = Network( 'rdma', '4.4.4.0/24' )
        self.add_network( rdmanet )

        beagles = HostGroup( 'beagles' )
        for i in range( 1, 6 ):
            pubip  = QualifiedIPAddress( pubnet, '134.169.35.' + str(97 + i) )
            pubint = NetworkInterface( 'eth0', [pubip] )
            pr0ip  = QualifiedIPAddress( pr0net, '10.10.10.' + str(10 + i) )
            pr0int = NetworkInterface( 'eth1', [pr0ip] )
            pr1ip  = QualifiedIPAddress( pr1net, '10.10.10.' + str(210 + i) )
            pr1int = NetworkInterface( 'eth2', [pr1ip] )
            pr2ip  = QualifiedIPAddress( pr2net, '10.10.10.' + str(90 + i) )
            pr2int = NetworkInterface( 'eth3', [pr2ip] )
            rdmip  = QualifiedIPAddress( rdmanet, '4.4.4.' + str(i) )
            rdmint = NetworkInterface( 'eth4:2' if i==1 else 'eth4:1', [rdmip] )
            host   = Host( 'beagle' + str(i), 'ibr.cs.tu-bs.de', [pubint, pr0int, pr1int, pr2int, rdmint] )
            beagles.append( host )
        self.add_hostgroup( beagles )

        clouds = HostGroup( 'clouds' )
        for i in range( 1, 7 ):
            pubip  = QualifiedIPAddress( pubnet, '134.169.35.' + str(200 + i) )
            pubint = NetworkInterface( 'eth0', [pubip] )
            pr0ip  = QualifiedIPAddress( pr0net, '10.10.10.' + str(i) )
            pr0int = NetworkInterface( 'eth1', [pr0ip] )
            pr1ip  = QualifiedIPAddress( pr1net, '10.10.10.' + str(200 + i) )
            pr1int = NetworkInterface( 'eth1:1', [pr1ip] )
            pr2ip  = QualifiedIPAddress( pr2net, '10.10.10.' + str(80 + i) )
            pr2int = NetworkInterface( 'eth1:2', [pr2ip] )
            host   = Host( 'cloud' + str(i), 'ibr.cs.tu-bs.de', [pubint, pr0int, pr1int, pr2int] )
            clouds.append( host )
        self.add_hostgroup( clouds )

        beagles4 = HostGroup( 'beagles4', beagles[ :4 ] )
        self.add_hostgroup( beagles4 )
        cloudsb = HostGroup( 'cloudsb', clouds[ :4 ] )
        self.add_hostgroup( cloudsb )
        cloudss = HostGroup( 'cloudss', clouds[ 4: ] )
        self.add_hostgroup( cloudss )

        dsgxnet1 = Network( 'dsgxnet1', '10.10.1.0/24' )
        self.add_network( dsgxnet1 )
        dsgxnet2 = Network( 'dsgxnet2', '10.10.2.0/24' )
        self.add_network( dsgxnet2 )
        dsgxnet3 = Network( 'dsgxnet3', '10.10.3.0/24' )
        self.add_network( dsgxnet3 )
        dsgxnet4 = Network( 'dsgxnet4', '10.10.4.0/24' )
        self.add_network( dsgxnet4 )

        dsgxs = HostGroup( 'dsgxs' )
        for i in range( 1, 6 ):
            pubip    = QualifiedIPAddress( pubnet, '134.169.35.' + str(108 + i) )
            pubint   = NetworkInterface( 'eth0', [pubip] )
            dsgx1ip  = QualifiedIPAddress( dsgxnet1, '10.10.1.' + str(i) )
            dsgx1int = NetworkInterface( 'eth1', [dsgx1ip] )
            dsgx2ip  = QualifiedIPAddress( dsgxnet2, '10.10.2.' + str(i) )
            dsgx2int = NetworkInterface( 'eth2', [dsgx2ip] )
            dsgx3ip  = QualifiedIPAddress( dsgxnet3, '10.10.3.' + str(i) )
            dsgx3int = NetworkInterface( 'eth3', [dsgx3ip] )
            dsgx4ip  = QualifiedIPAddress( dsgxnet4, '10.10.4.' + str(i) )
            dsgx4int = NetworkInterface( 'eth4', [dsgx4ip] )
            host   = Host( 'dsgx' + str(i), 'ibr.cs.tu-bs.de', [pubint, dsgx1int, dsgx2int, dsgx3int, dsgx4int] )
            dsgxs.append( host )
        # pubip = QualifiedIPAddress( pubnet, '134.169.35.' + str(108 + 5) )
        # pubint   = NetworkInterface( 'eth0', [pubip] )
        # dsgx1ip  = QualifiedIPAddress( dsgxnet1, '10.10.1.' + str(5) )
        # dsgx1int = NetworkInterface( 'eth1', [dsgx1ip] )
        # dsgx2ip  = QualifiedIPAddress( dsgxnet2, '10.10.2.' + str(5) )
        # dsgx2int = NetworkInterface( 'eth2', [dsgx2ip] )
        # dsgx3ip  = QualifiedIPAddress( dsgxnet3, '10.10.3.' + str(5) )
        # dsgx3int = NetworkInterface( 'eth3', [dsgx3ip] )
        # dsgx4ip  = QualifiedIPAddress( dsgxnet4, '10.10.4.' + str(5) )
        # dsgx4int = NetworkInterface( 'eth4', [dsgx4ip] )
        # host   = Host( 'dsgx' + str(5), 'ibr.cs.tu-bs.de', [pubint, dsgx1int, dsgx2int, dsgx3int, dsgx4int] )
        # dsgxs.append( host )
        self.add_hostgroup( dsgxs )

        dsgxss = HostGroup( 'dsgxss', dsgxs[ :4 ] )
        self.add_hostgroup( dsgxss )
        dsgxsc = HostGroup( 'dsgxsc', dsgxs[ 4: ] )
        self.add_hostgroup( dsgxsc )


def load_network_config():
    return IBRNetworkConfig()
